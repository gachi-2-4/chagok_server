package gachi.chagok_server.domain.chat.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiClientException.Kind;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.ChatAnswer;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.Suggestion;
import gachi.chagok_server.domain.chat.dto.ChatDtos.*;
import gachi.chagok_server.domain.chat.service.ChatStore.AskContext;
import gachi.chagok_server.domain.chat.service.ChatStore.ChatLinks;
import gachi.chagok_server.domain.chat.service.ChatStore.MessagePage;
import gachi.chagok_server.domain.chat.support.QuestionRateLimiter;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CHAT_ID = 12L;

    @Mock ChatStore store;
    @Mock ChatSuggestionService suggestions;
    @Mock AiServerClient ai;
    @Mock QuestionRateLimiter rateLimiter;
    @Mock gachi.chagok_server.domain.document.service.DocumentReanalysisService reanalysis;
    @InjectMocks ChatService service;

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private static ChatAnswer answer() {
        return new ChatAnswer("매월 **25일**까지 내면 돼요.", "ANSWERED", List.of(Map.of("sourceType", "DOCUMENT")), null, List.of("늦게 내면?"));
    }

    private static ChatLinks linksWith(DocumentView... documents) {
        return new ChatLinks(CHAT_ID, null, null, List.of(), List.of(documents));
    }

    @Test
    @DisplayName("대화를 만들면 연결된 서류와 인사말, 질문 칩을 함께 준다")
    void create() {
        // given
        LinkRequest request = new LinkRequest(List.of(501L), List.of());
        given(store.create(USER_ID, request)).willReturn(linksWith(new DocumentView(501L, "임대차계약서", LinkStatus.LINKED, null)));
        given(suggestions.forUser(USER_ID)).willReturn(List.of(new Suggestion("보증금 언제 돌려받아?", "AMOUNT")));

        // when
        CreateResponse response = service.create(USER_ID, request);

        // then
        assertThat(response.chatId()).isEqualTo(CHAT_ID);
        assertThat(response.title()).isNull();
        assertThat(response.greeting()).isEqualTo(ChatService.GREETING_WITH_DOCUMENTS);
        assertThat(response.suggestions()).hasSize(1);
    }

    @Test
    @DisplayName("서류 없이 만든 대화는 서류를 넣어 달라는 인사말을 준다")
    void createWithoutDocuments() {
        // given
        given(store.create(USER_ID, new LinkRequest(null, null))).willReturn(linksWith());
        given(suggestions.forUser(USER_ID)).willReturn(List.of());

        // when
        CreateResponse response = service.create(USER_ID, null);

        // then
        assertThat(response.greeting()).isEqualTo(ChatService.GREETING_WITHOUT_DOCUMENTS);
        assertThat(response.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("질문하면 연결된 서류로 AI에 묻고, 답을 받은 뒤 질문과 답변을 함께 저장한다")
    void ask() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(store.forQuestion(USER_ID, CHAT_ID)).willReturn(new AskContext(List.of(501L, 570L)));
        ChatAnswer answer = answer();
        given(ai.chatAnswer(CHAT_ID, USER_ID, List.of(501L, 570L), "월세는 언제 내야 하나요?")).willReturn(answer);
        AskResponse saved = new AskResponse(null, null);
        given(store.saveExchange(USER_ID, CHAT_ID, "월세는 언제 내야 하나요?", answer)).willReturn(saved);

        // when
        AskResponse response = service.ask(USER_ID, CHAT_ID, "  월세는 언제 내야 하나요?  ");

        // then
        assertThat(response).isSameAs(saved);
    }

    @Test
    @DisplayName("질문이 비었거나 500자를 넘으면 INVALID_QUESTION이고 AI를 부르지 않는다")
    void invalidQuestion() {
        assertError(() -> service.ask(USER_ID, CHAT_ID, "   "), ErrorCode.INVALID_QUESTION);
        assertError(() -> service.ask(USER_ID, CHAT_ID, null), ErrorCode.INVALID_QUESTION);
        assertError(() -> service.ask(USER_ID, CHAT_ID, "가".repeat(501)), ErrorCode.INVALID_QUESTION);

        verifyNoInteractions(ai, rateLimiter);
    }

    @Test
    @DisplayName("분당 10회를 넘기면 RATE_LIMITED")
    void rateLimited() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(false);

        // when & then
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.RATE_LIMITED);
        verifyNoInteractions(ai);
    }

    @Test
    @DisplayName("연결된 서류가 없거나 모두 분석 중이면 AI를 부르지 않는다")
    void noReadyDocuments() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(store.forQuestion(USER_ID, CHAT_ID))
                .willThrow(new BusinessException(ErrorCode.NO_LINKED_DOCUMENT));

        // when & then
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.NO_LINKED_DOCUMENT);
        verifyNoInteractions(ai);
    }

    @Test
    @DisplayName("AI 오류는 429·409(서류 미인덱스)·504·502로 바꿔 주고, 아무것도 저장하지 않는다")
    void aiErrors() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(store.forQuestion(USER_ID, CHAT_ID)).willReturn(new AskContext(List.of(501L)));
        given(ai.chatAnswer(any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.RATE_LIMITED, "429"))
                .willThrow(new AiClientException(Kind.INDEX_NOT_FOUND, "404"))
                .willThrow(new AiClientException(Kind.TIMEOUT, "timeout"))
                .willThrow(new AiClientException(Kind.SERVER_ERROR, "500"));

        // when & then
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.RATE_LIMITED);
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.DOCUMENT_NOT_INDEXED);
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.AI_TIMEOUT);
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.AI_ERROR);
        verify(store, never()).saveExchange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("앱이 메시지를 조회하면 질문 칩을 새로 받고, AI 서버가 조회하면 칩을 받지 않는다")
    void messagesSuggestions() {
        // given
        given(store.messages(USER_ID, CHAT_ID, null, 30)).willReturn(new MessagePage(linksWith(), List.of(), false));
        given(suggestions.forUser(USER_ID)).willReturn(List.of(new Suggestion("질문", "GENERAL")));

        // when
        MessagesResponse forApp = service.messages(USER_ID, CHAT_ID, null, 30, true);
        MessagesResponse forAi = service.messages(USER_ID, CHAT_ID, null, 30, false);

        // then
        assertThat(forApp.suggestions()).hasSize(1);
        assertThat(forAi.suggestions()).isEmpty();
        verify(suggestions, org.mockito.Mockito.times(1)).forUser(USER_ID);
    }

    @Test
    @DisplayName("메시지 조회 size와 목록 조회 page·size 범위를 검사한다")
    void paramValidation() {
        assertError(() -> service.messages(USER_ID, CHAT_ID, null, 0, true), ErrorCode.INVALID_INPUT);
        assertError(() -> service.messages(USER_ID, CHAT_ID, null, 101, true), ErrorCode.INVALID_INPUT);
        assertError(() -> service.list(USER_ID, null, -1, 20), ErrorCode.INVALID_INPUT);
        assertError(() -> service.list(USER_ID, null, 0, 51), ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("서류를 바꿔도 질문 칩은 다시 받지 않는다")
    void replaceDocuments() {
        // given
        LinkRequest request = new LinkRequest(List.of(501L), List.of(3L));
        given(store.replaceLinks(USER_ID, CHAT_ID, request)).willReturn(linksWith(new DocumentView(501L, "임대차계약서", LinkStatus.LINKED, null)));

        // when
        LinksResponse response = service.replaceDocuments(USER_ID, CHAT_ID, request);

        // then
        assertThat(response.documents()).hasSize(1);
        verifyNoInteractions(suggestions);
    }

    @Test
    @DisplayName("AI가 서류를 잃어버리면(INDEX_NOT_FOUND) 연결된 서류의 분석을 다시 요청하고 409로 알린다")
    void indexNotFoundRequestsReanalysis() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(store.forQuestion(USER_ID, CHAT_ID)).willReturn(new AskContext(List.of(501L, 570L)));
        given(ai.chatAnswer(any(), any(), any(), any())).willThrow(new AiClientException(Kind.INDEX_NOT_FOUND, "404"));

        // when & then
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.DOCUMENT_NOT_INDEXED);
        verify(reanalysis).requestRecovery(USER_ID, List.of(501L, 570L));
    }

    @Test
    @DisplayName("분석 재요청이 실패해도 앱에는 같은 409를 준다")
    void reanalysisFailureDoesNotMaskError() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(store.forQuestion(USER_ID, CHAT_ID)).willReturn(new AskContext(List.of(501L)));
        given(ai.chatAnswer(any(), any(), any(), any())).willThrow(new AiClientException(Kind.INDEX_NOT_FOUND, "404"));
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 오류")).when(reanalysis).requestRecovery(any(), any());

        // when & then
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.DOCUMENT_NOT_INDEXED);
    }

    @Test
    @DisplayName("INDEX_NOT_FOUND가 아닌 AI 오류에서는 서류를 다시 분석하지 않는다")
    void otherErrorsDoNotReanalyze() {
        // given
        given(rateLimiter.tryAcquire(USER_ID)).willReturn(true);
        given(store.forQuestion(USER_ID, CHAT_ID)).willReturn(new AskContext(List.of(501L)));
        given(ai.chatAnswer(any(), any(), any(), any())).willThrow(new AiClientException(Kind.SERVER_ERROR, "500"));

        // when & then
        assertError(() -> service.ask(USER_ID, CHAT_ID, "질문"), ErrorCode.AI_ERROR);
        verifyNoInteractions(reanalysis);
    }
}
