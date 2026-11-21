package gachi.chagok_server.domain.chat.service;

import gachi.chagok_server.domain.ai.dto.AiChatDtos.ChatAnswer;
import gachi.chagok_server.domain.chat.dto.ChatDtos.*;
import gachi.chagok_server.domain.chat.entity.Chat;
import gachi.chagok_server.domain.chat.entity.ChatBoxLink;
import gachi.chagok_server.domain.chat.entity.ChatDocumentLink;
import gachi.chagok_server.domain.chat.entity.ChatMessage;
import gachi.chagok_server.domain.chat.entity.ChatRole;
import gachi.chagok_server.domain.chat.repository.ChatBoxLinkRepository;
import gachi.chagok_server.domain.chat.repository.ChatDocumentLinkRepository;
import gachi.chagok_server.domain.chat.repository.ChatMessageRepository;
import gachi.chagok_server.domain.chat.repository.ChatRepository;
import gachi.chagok_server.domain.chat.support.ChatLinkResolver;
import gachi.chagok_server.domain.chat.support.ChatLinkResolver.Linked;
import gachi.chagok_server.domain.chat.support.CitationEnricher;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 대화·연결 서류·메시지의 저장과 조회. AI 서버 호출은 {@link ChatService}가 트랜잭션 밖에서 하고,
 * 이 클래스는 호출 앞뒤의 짧은 트랜잭션만 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatStore {

    private final ChatRepository chats;
    private final ChatMessageRepository messages;
    private final ChatDocumentLinkRepository documentLinks;
    private final ChatBoxLinkRepository boxLinks;
    private final UserRepository users;
    private final ChatLinkResolver resolver;
    private final CitationEnricher citations;
    private final DocumentJson json;

    /** 대화 하나와 연결된 박스·서류 */
    public record ChatLinks(Long chatId, String title, java.time.OffsetDateTime createdAt, List<BoxView> boxes,
                            List<DocumentView> documents) {
    }

    public record MessagePage(ChatLinks links, List<MessageView> messages, boolean hasMore) {
    }

    /** 질문하려고 읽은 값: AI에 보낼 서류(분석이 끝난 LINKED만) */
    public record AskContext(List<Long> linkedDocumentIds) {
    }

    @Transactional
    public ChatLinks create(Long userId, LinkRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Set<Long> documentIds = distinct(request.documentIds());
        Set<Long> boxIds = distinct(request.boxIds());
        resolver.validate(userId, documentIds, boxIds);

        Chat chat = chats.saveAndFlush(new Chat(user));
        saveLinks(chat, documentIds, boxIds);
        return links(userId, chat);
    }

    /** 넣을 서류·박스 전체로 교체한다. 지난 메시지는 그대로 둔다. */
    @Transactional
    public ChatLinks replaceLinks(Long userId, Long chatId, LinkRequest request) {
        Chat chat = find(userId, chatId);
        Set<Long> documentIds = distinct(request.documentIds());
        Set<Long> boxIds = distinct(request.boxIds());
        resolver.validate(userId, documentIds, boxIds);

        documentLinks.deleteByChatId(chatId);
        boxLinks.deleteByChatId(chatId);
        saveLinks(chat, documentIds, boxIds);
        return links(userId, chat);
    }

    public ChatLinks links(Long userId, Long chatId) {
        return links(userId, find(userId, chatId));
    }

    public PageResponse<ListItem> list(Long userId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Chat> found = keyword == null || keyword.isBlank()
                ? chats.findListed(userId, pageable)
                : chats.searchListed(userId, likePattern(keyword.trim()), pageable);
        return PageResponse.of(found, chat -> new ListItem(chat.getId(), chat.getTitle(), listDocuments(userId, chat),
                KstTime.toOffset(chat.getLastMessageAt())));
    }

    /** 최신 메시지부터 size개를 읽어 오래된 순으로 돌려준다. beforeMessageId보다 오래된 것만 읽는다. */
    public MessagePage messages(Long userId, Long chatId, Long beforeMessageId, int size) {
        Chat chat = find(userId, chatId);
        // 더 오래된 메시지가 있는지 알기 위해 하나 더 읽는다.
        Pageable pageable = PageRequest.of(0, size + 1);
        List<ChatMessage> newestFirst = beforeMessageId == null
                ? messages.findByChatIdOrderByIdDesc(chatId, pageable)
                : messages.findByChatIdAndIdLessThanOrderByIdDesc(chatId, beforeMessageId, pageable);
        boolean hasMore = newestFirst.size() > size;
        List<ChatMessage> page = new ArrayList<>(hasMore ? newestFirst.subList(0, size) : newestFirst);
        Collections.reverse(page);
        return new MessagePage(links(userId, chat), page.stream().map(this::toView).toList(), hasMore);
    }

    /** 질문할 수 있는 상태인지 확인하고 AI에 보낼 서류를 고른다. 박스를 넣은 대화는 질문할 때마다 현재 서류로 펼친다. */
    public AskContext forQuestion(Long userId, Long chatId) {
        Chat chat = find(userId, chatId);
        List<Linked> linked = expand(userId, chat).stream().filter(l -> !l.isDeleted()).toList();
        if (linked.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_LINKED_DOCUMENT);
        }
        List<Long> ready = linked.stream().filter(l -> l.linkStatus() == LinkStatus.LINKED).map(Linked::documentId).toList();
        if (ready.isEmpty()) {
            // 연결된 서류가 모두 분석 중이다.
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        return new AskContext(ready);
    }

    /** 질문과 답변을 함께 저장한다. AI 응답을 받은 뒤에만 저장하므로 실패하면 아무것도 남지 않는다. */
    @Transactional
    public AskResponse saveExchange(Long userId, Long chatId, String question, ChatAnswer answer) {
        Chat chat = find(userId, chatId);
        ChatMessage questionMessage = messages.save(ChatMessage.question(chat, question));
        chat.nameFromFirstQuestion(question);

        List<java.util.Map<String, Object>> enriched = citations.enrich(answer.citations());
        List<String> followUps = answer.followUpQuestions() == null ? List.of() : answer.followUpQuestions();
        ChatMessage answerMessage = messages.save(ChatMessage.answer(chat, answer.answer() == null ? "" : answer.answer(),
                answer.answerType(), json.write(enriched), answer.disclaimer(), json.write(followUps)));
        chat.touch(Instant.now());
        messages.flush();
        return new AskResponse(toView(questionMessage), toView(answerMessage));
    }

    // ---- 내부 ----

    private Chat find(Long userId, Long chatId) {
        return chats.findByIdAndUserId(chatId, userId).orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_FOUND));
    }

    private void saveLinks(Chat chat, Set<Long> documentIds, Set<Long> boxIds) {
        documentIds.forEach(id -> documentLinks.save(new ChatDocumentLink(chat, id)));
        boxIds.forEach(id -> boxLinks.save(new ChatBoxLink(chat, id)));
    }

    private List<Linked> expand(Long userId, Chat chat) {
        return resolver.expand(userId, documentIdsOf(chat), boxIdsOf(chat));
    }

    private Set<Long> documentIdsOf(Chat chat) {
        return documentLinks.findByChatIdOrderByIdAsc(chat.getId()).stream()
                .map(ChatDocumentLink::getDocumentId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> boxIdsOf(Chat chat) {
        return boxLinks.findByChatIdOrderByIdAsc(chat.getId()).stream()
                .map(ChatBoxLink::getBoxId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ChatLinks links(Long userId, Chat chat) {
        List<Linked> linked = expand(userId, chat);
        return new ChatLinks(chat.getId(), chat.getTitle(), KstTime.toOffset(chat.getCreatedAt()),
                resolver.boxViews(userId, boxIdsOf(chat)), resolver.documentViews(linked));
    }

    /** 지난 대화 목록의 서류. 삭제된 서류는 deleted=true, "삭제된 서류"로 준다. */
    private List<ListDocument> listDocuments(Long userId, Chat chat) {
        return expand(userId, chat).stream()
                .map(l -> new ListDocument(l.documentId(), l.title(), l.isDeleted()))
                .toList();
    }

    private MessageView toView(ChatMessage message) {
        boolean answer = message.getRole() == ChatRole.ASSISTANT;
        return new MessageView(message.getId(), message.getRole(), message.getContent(),
                answer ? message.getAnswerType() : null,
                answer ? json.readMaps(message.getCitationsJson()) : null,
                answer ? message.getDisclaimer() : null,
                answer ? json.readStrings(message.getFollowUpQuestionsJson()) : null,
                KstTime.toOffset(message.getCreatedAt()));
    }

    private static Set<Long> distinct(List<Long> ids) {
        return ids == null ? Set.of() : ids.stream().filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** LIKE의 특수문자(% _ \)를 문자 그대로 찾도록 막는다. */
    static String likePattern(String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
