package gachi.chagok_server.domain.chat.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.ChatAnswer;
import gachi.chagok_server.domain.chat.dto.ChatDtos.*;
import gachi.chagok_server.domain.chat.service.ChatStore.AskContext;
import gachi.chagok_server.domain.chat.service.ChatStore.ChatLinks;
import gachi.chagok_server.domain.chat.service.ChatStore.MessagePage;
import gachi.chagok_server.domain.chat.support.QuestionRateLimiter;
import gachi.chagok_server.domain.document.service.DocumentReanalysisService;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 채팅. AI 서버 호출(최대 30초)이 DB 연결을 붙잡지 않도록 이 클래스는 트랜잭션을 열지 않고,
 * 저장·조회는 {@link ChatStore}의 짧은 트랜잭션에 맡긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int MAX_QUESTION_LENGTH = 500;
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_MESSAGE_SIZE = 100;
    static final String GREETING_WITH_DOCUMENTS = "안녕하세요! 넣어 주신 서류에서 궁금한 내용을 쉽게 설명해 드릴게요.";
    static final String GREETING_WITHOUT_DOCUMENTS = "안녕하세요! 보관함에서 서류를 넣어 주시면 궁금한 내용을 쉽게 설명해 드릴게요.";

    private final ChatStore store;
    private final ChatSuggestionService suggestions;
    private final AiServerClient ai;
    private final QuestionRateLimiter rateLimiter;
    private final DocumentReanalysisService reanalysis;

    /** 새 대화를 만든다. 질문 칩을 받지 못해도(AI 실패) 대화는 만들어진다. */
    public CreateResponse create(Long userId, LinkRequest request) {
        ChatLinks links = store.create(userId, request == null ? new LinkRequest(null, null) : request);
        return new CreateResponse(links.chatId(), links.title(), links.boxes(), links.documents(), greeting(links),
                suggestions.forUser(userId), links.createdAt());
    }

    public PageResponse<ListItem> list(Long userId, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return store.list(userId, keyword, page, size);
    }

    /**
     * 대화 메시지 조회. 앱이 부르면 질문 칩도 새로 받는다(화면을 열 때마다).
     * AI 서버가 부르는 경우(includeSuggestions=false)에는 칩을 받지 않는다.
     */
    public MessagesResponse messages(Long userId, Long chatId, Long beforeMessageId, int size, boolean includeSuggestions) {
        if (size < 1 || size > MAX_MESSAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        MessagePage page = store.messages(userId, chatId, beforeMessageId, size);
        ChatLinks links = page.links();
        return new MessagesResponse(links.chatId(), links.title(), links.boxes(), links.documents(), greeting(links),
                includeSuggestions ? suggestions.forUser(userId) : List.of(), page.messages(), page.hasMore());
    }

    /** 넣을 서류·박스 전체로 바꾼다. 질문 칩은 내 보관함 서류 종류 기준이라 다시 받지 않는다. */
    public LinksResponse replaceDocuments(Long userId, Long chatId, LinkRequest request) {
        ChatLinks links = store.replaceLinks(userId, chatId, request == null ? new LinkRequest(null, null) : request);
        return new LinksResponse(links.chatId(), links.boxes(), links.documents());
    }

    /**
     * 질문을 보내고 AI 답변을 받는다. AI 응답을 받은 뒤에 질문과 답변을 함께 저장하므로
     * AI가 최근 대화를 가져갈 때 지금 질문이 중복으로 들어가지 않고, 실패하면 아무것도 저장되지 않아 다시 보내도 중복되지 않는다.
     */
    public AskResponse ask(Long userId, Long chatId, String content) {
        String question = content == null ? "" : content.trim();
        if (question.isEmpty() || question.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_QUESTION);
        }
        if (!rateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        AskContext context = store.forQuestion(userId, chatId);

        ChatAnswer answer;
        try {
            answer = ai.chatAnswer(chatId, userId, context.linkedDocumentIds(), question);
        } catch (AiClientException e) {
            if (e.getKind() == AiClientException.Kind.INDEX_NOT_FOUND) {
                // AI가 서류를 잃어버렸다. 사용자가 고친 값은 유지하면서 서류 분석을 다시 요청하고, 끝날 때까지 잠시 기다리게 한다.
                requestReanalysis(userId, context.linkedDocumentIds());
            }
            throw translate(e);
        }
        return store.saveExchange(userId, chatId, question, answer);
    }

    private void requestReanalysis(Long userId, List<Long> documentIds) {
        try {
            reanalysis.requestRecovery(userId, documentIds);
        } catch (RuntimeException e) {
            // 복구 요청이 실패해도 앱에는 "다시 정리하고 있어요" 대신 아래 오류 그대로 알린다.
            log.warn("서류 분석 재요청에 실패했어요: userId={}", userId, e);
        }
    }

    private String greeting(ChatLinks links) {
        return links.documents().isEmpty() ? GREETING_WITHOUT_DOCUMENTS : GREETING_WITH_DOCUMENTS;
    }

    /**
     * AI 서버 오류를 앱에 줄 오류로 바꾼다: 429는 429, 시간 초과는 504, 나머지는 502.
     * AI가 서류를 찾지 못하면(INDEX_NOT_FOUND) 서류 분석을 다시 요청하고(사용자가 고친 값은 유지) 409로 알린다.
     */
    static BusinessException translate(AiClientException e) {
        return switch (e.getKind()) {
            case RATE_LIMITED -> new BusinessException(ErrorCode.RATE_LIMITED, e);
            case INDEX_NOT_FOUND -> new BusinessException(ErrorCode.DOCUMENT_NOT_INDEXED, e);
            case TIMEOUT -> new BusinessException(ErrorCode.AI_TIMEOUT, e);
            default -> new BusinessException(ErrorCode.AI_ERROR, e);
        };
    }
}
