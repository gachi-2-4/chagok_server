package gachi.chagok_server.domain.chat.controller;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.chat.dto.ChatDtos.AskResponse;
import gachi.chagok_server.domain.chat.dto.ChatDtos.MessageView;
import gachi.chagok_server.domain.chat.entity.ChatRole;
import gachi.chagok_server.domain.chat.service.ChatService;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.security.InternalKeyVerifier;
import gachi.chagok_server.global.security.JwtTokenProvider;
import gachi.chagok_server.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, InternalKeyVerifier.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean ChatService chatService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    private String bearer() {
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));
        return "Bearer " + token;
    }

    private static AskResponse exchange() {
        return new AskResponse(
                new MessageView(901L, ChatRole.USER, "월세는 언제 내야 하나요?", null, null, null, null, null),
                new MessageView(902L, ChatRole.ASSISTANT, "매월 **25일**까지 내면 돼요.", "ANSWERED",
                        List.of(Map.of("sourceType", "DOCUMENT", "label", "계약서 1페이지")), null, List.of("늦게 내면?"), null));
    }

    @Test
    @DisplayName("AI 서버는 내부 키와 userId로 최근 대화를 가져가고, 이때는 질문 칩을 받지 않는다")
    void internalMessagesRead() throws Exception {
        mockMvc.perform(get("/api/v1/chats/12/messages").param("userId", "1024").param("size", "10")
                        .header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isOk());

        verify(chatService).messages(1024L, 12L, null, 10, false);
    }

    @Test
    @DisplayName("내부 키가 틀리면 403, userId가 없으면 400")
    void internalReadRejected() throws Exception {
        mockMvc.perform(get("/api/v1/chats/12/messages").param("userId", "1").header("X-Internal-Key", "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/chats/12/messages").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("내부 키로는 메시지 조회 외의 대화 API를 호출할 수 없다")
    void internalKeyDoesNotOpenOtherApis() throws Exception {
        mockMvc.perform(get("/api/v1/chats").header("X-Internal-Key", "internal-test-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/chats/12/messages").header("X-Internal-Key", "internal-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"질문\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("앱은 토큰으로 메시지를 조회하고, 이때는 질문 칩도 받는다")
    void appMessagesRead() throws Exception {
        mockMvc.perform(get("/api/v1/chats/12/messages").param("beforeMessageId", "901").header("Authorization", bearer()))
                .andExpect(status().isOk());

        verify(chatService).messages(1L, 12L, 901L, 30, true);
    }

    @Test
    @DisplayName("질문을 보내면 201과 질문·답변을 돌려준다")
    void ask() throws Exception {
        // given
        given(chatService.ask(eq(1L), eq(12L), eq("월세는 언제 내야 하나요?"))).willReturn(exchange());

        // when & then
        mockMvc.perform(post("/api/v1/chats/12/messages").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"월세는 언제 내야 하나요?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.question.messageId").value(901))
                .andExpect(jsonPath("$.data.question.role").value("USER"))
                .andExpect(jsonPath("$.data.answer.answerType").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer.citations[0].label").value("계약서 1페이지"))
                .andExpect(jsonPath("$.data.answer.followUpQuestions[0]").value("늦게 내면?"));
    }

    @Test
    @DisplayName("stream=true면 delta → citations → done 순서의 SSE로 준다")
    void askStreaming() throws Exception {
        // given
        given(chatService.ask(eq(1L), eq(12L), any())).willReturn(exchange());

        // when
        MvcResult started = mockMvc.perform(post("/api/v1/chats/12/messages").param("stream", "true").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"월세는 언제 내야 하나요?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn();

        // then
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:delta").contains("event:citations").contains("event:done");
        assertThat(body.indexOf("event:delta")).isLessThan(body.indexOf("event:citations"));
        assertThat(body.indexOf("event:citations")).isLessThan(body.indexOf("event:done"));
        assertThat(body).contains("\"questionMessageId\":901").contains("\"answerMessageId\":902");
    }

    @Test
    @DisplayName("스트림을 시작하기 전에 난 오류는 일반 오류 응답으로 준다")
    void askStreamingError() throws Exception {
        // given
        given(chatService.ask(any(), any(), any())).willThrow(new BusinessException(ErrorCode.RATE_LIMITED));

        // when & then
        mockMvc.perform(post("/api/v1/chats/12/messages").param("stream", "true").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"질문\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("질문이 빠지면 400")
    void askWithoutContent() throws Exception {
        mockMvc.perform(post("/api/v1/chats/12/messages").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("대화를 만들 때 Body가 없어도 된다")
    void createWithoutBody() throws Exception {
        mockMvc.perform(post("/api/v1/chats").header("Authorization", bearer()))
                .andExpect(status().isCreated());

        verify(chatService).create(1L, null);
    }

    @Test
    @DisplayName("서류를 바꿀 때 없는 대화는 404")
    void replaceDocumentsNotFound() throws Exception {
        // given
        given(chatService.replaceDocuments(eq(1L), eq(99L), any())).willThrow(new BusinessException(ErrorCode.CHAT_NOT_FOUND));

        // when & then
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/chats/99/documents")
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"documentIds\":[501]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_NOT_FOUND"));
    }
}
