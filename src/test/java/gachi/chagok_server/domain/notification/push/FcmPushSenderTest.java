package gachi.chagok_server.domain.notification.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FcmPushSenderTest {

    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/chagok-test/messages:send";

    private MockRestServiceServer server;
    private FcmAccessTokenProvider tokens;
    private FcmPushSender sender;

    private final PushMessage message = new PushMessage("fcm-token-1", "계약 만료까지 7일 남았어요", "2월 28일까지 계약 만료를 확인해 주세요.",
            Map.of("notificationId", "3010", "type", "DEADLINE_DUE", "documentId", "501"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fcm.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        tokens = mock(FcmAccessTokenProvider.class);
        given(tokens.accessToken()).willReturn("ya29.token");
        sender = new FcmPushSender(builder.build(), tokens, "chagok-test", JsonMapper.builder().build());
    }

    @Test
    @DisplayName("FCM v1 형식으로 토큰·제목·본문·data를 보내고 SENT를 돌려준다")
    void sends() {
        // given
        server.expect(requestTo(SEND_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ya29.token"))
                .andExpect(jsonPath("$.message.token").value("fcm-token-1"))
                .andExpect(jsonPath("$.message.notification.title").value("계약 만료까지 7일 남았어요"))
                .andExpect(jsonPath("$.message.notification.body").value("2월 28일까지 계약 만료를 확인해 주세요."))
                .andExpect(jsonPath("$.message.data.notificationId").value("3010"))
                .andExpect(jsonPath("$.message.data.type").value("DEADLINE_DUE"))
                .andExpect(jsonPath("$.message.data.documentId").value("501"))
                .andExpect(jsonPath("$.message.android.priority").value("HIGH"))
                .andRespond(withSuccess("{\"name\":\"projects/chagok-test/messages/1\"}", MediaType.APPLICATION_JSON));

        // when
        PushResult result = sender.send(message);

        // then
        assertThat(result).isEqualTo(PushResult.SENT);
        server.verify();
    }

    @Test
    @DisplayName("토큰이 만료됐다고(404 UNREGISTERED) 하면 TOKEN_INVALID")
    void unregistered() {
        // given
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"));

        // when & then
        assertThat(sender.send(message)).isEqualTo(PushResult.TOKEN_INVALID);
    }

    @Test
    @DisplayName("본문이 없는 404도 만료된 토큰으로 본다")
    void notFoundWithoutBody() {
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(sender.send(message)).isEqualTo(PushResult.TOKEN_INVALID);
    }

    @Test
    @DisplayName("잘못된 요청(400)·서버 오류(5xx)·할당량(429)은 토큰을 지우지 않고 FAILED")
    void otherErrorsKeepToken() {
        // given
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}"));
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when & then
        assertThat(sender.send(message)).isEqualTo(PushResult.FAILED);
        assertThat(sender.send(message)).isEqualTo(PushResult.FAILED);
        assertThat(sender.send(message)).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("401이면 액세스 토큰을 새로 받아 한 번만 다시 시도한다")
    void retriesOnceOnUnauthorized() {
        // given
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo(SEND_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when
        PushResult result = sender.send(message);

        // then
        assertThat(result).isEqualTo(PushResult.SENT);
        verify(tokens).invalidate();
        verify(tokens, times(2)).accessToken();
        server.verify();
    }

    @Test
    @DisplayName("다시 시도해도 401이면 FAILED로 끝낸다 (무한 반복하지 않음)")
    void unauthorizedTwice() {
        // given
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThat(sender.send(message)).isEqualTo(PushResult.FAILED);
    }

    @Test
    @DisplayName("연결 오류나 토큰 발급 실패는 예외를 던지지 않고 FAILED")
    void exceptionsBecomeFailed() {
        // given
        server.expect(requestTo(SEND_URL)).andRespond(withException(new IOException("연결 끊김")));
        assertThat(sender.send(message)).isEqualTo(PushResult.FAILED);

        given(tokens.accessToken()).willThrow(new IllegalStateException("토큰 발급 실패"));
        assertThat(sender.send(message)).isEqualTo(PushResult.FAILED);
    }
}
