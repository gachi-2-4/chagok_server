package gachi.chagok_server.domain.chat.dto;

import gachi.chagok_server.domain.ai.dto.AiChatDtos.Suggestion;
import gachi.chagok_server.domain.chat.entity.ChatRole;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 채팅 API 요청·응답 DTO.
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    public enum LinkStatus { LINKED, PENDING }

    /** 대화에 넣을 서류·박스 (대화 시작·대화 서류 변경 공통). 둘 다 비워도 된다. */
    public record LinkRequest(List<Long> documentIds, List<Long> boxIds) {
    }

    public record BoxView(Long boxId, String name, long documentCount) {
    }

    /** 대화에 연결된 서류. 박스로 들어온 서류면 viaBoxId에 그 박스 ID가 들어간다. */
    public record DocumentView(Long documentId, String title, LinkStatus linkStatus, Long viaBoxId) {
    }

    public record CreateResponse(Long chatId, String title, List<BoxView> boxes, List<DocumentView> documents,
                                 String greeting, List<Suggestion> suggestions, OffsetDateTime createdAt) {
    }

    public record LinksResponse(Long chatId, List<BoxView> boxes, List<DocumentView> documents) {
    }

    // ---- 지난 대화 목록 ----

    public record ListDocument(Long documentId, String title, boolean deleted) {
    }

    public record ListItem(Long chatId, String title, List<ListDocument> documents, OffsetDateTime lastMessageAt) {
    }

    // ---- 메시지 ----

    /**
     * 대화 메시지. 질문(USER)에는 AI 답변 값이 없고 null이다.
     * citations·disclaimer·followUpQuestions·answerType은 AI 채팅 응답 그대로이며, 근거에는 BE가 documentTitle·label을 붙인다.
     */
    public record MessageView(Long messageId, ChatRole role, String content, String answerType,
                              List<Map<String, Object>> citations, String disclaimer, List<String> followUpQuestions,
                              OffsetDateTime createdAt) {
    }

    public record MessagesResponse(Long chatId, String title, List<BoxView> boxes, List<DocumentView> documents,
                                   String greeting, List<Suggestion> suggestions, List<MessageView> messages, boolean hasMore) {
    }

    // ---- 질문 보내기 ----

    public record AskRequest(@NotNull String content) {
    }

    public record AskResponse(MessageView question, MessageView answer) {
    }
}
