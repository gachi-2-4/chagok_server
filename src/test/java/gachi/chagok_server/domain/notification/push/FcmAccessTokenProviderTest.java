package gachi.chagok_server.domain.notification.push;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FcmAccessTokenProviderTest {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String EMAIL = "fcm@chagok-test.iam.gserviceaccount.com";

    /** 테스트에서 시간을 직접 움직이는 시계 */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-18T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private KeyPair keys;
    private MockRestServiceServer server;
    private MutableClock clock;
    private FcmAccessTokenProvider provider;

    /** 서비스 계정 키 JSON의 private_key처럼 PEM 형식으로 만든다. */
    static String pem(KeyPair keys) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock();
        provider = new FcmAccessTokenProvider(EMAIL, pem(keys), TOKEN_URI, builder.build(), clock);
    }

    private void expectTokenRequest(String token, long expiresIn, AtomicReference<String> body) {
        server.expect(requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(request -> body.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + ",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("서비스 계정 키로 서명한 JWT를 보내 액세스 토큰을 받는다")
    void requestsTokenWithSignedAssertion() {
        // given
        AtomicReference<String> body = new AtomicReference<>();
        expectTokenRequest("ya29.first", 3600, body);

        // when
        String token = provider.accessToken();

        // then
        assertThat(token).isEqualTo("ya29.first");
        assertThat(body.get()).contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer");
        String assertion = body.get().substring(body.get().indexOf("assertion=") + "assertion=".length());
        Claims claims = Jwts.parser().verifyWith(keys.getPublic()).clock(() -> java.util.Date.from(clock.instant())).build().parseSignedClaims(assertion).getPayload();
        assertThat(claims.getIssuer()).isEqualTo(EMAIL);
        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.getAudience()).containsExactly(TOKEN_URI);
        assertThat(claims.get("scope", String.class)).isEqualTo(FcmAccessTokenProvider.SCOPE);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(clock.instant().plus(Duration.ofHours(1)));
        server.verify();
    }

    @Test
    @DisplayName("만료 전에는 받은 토큰을 다시 쓰고, 만료가 가까우면 새로 받는다")
    void cachesUntilNearExpiry() {
        // given
        expectTokenRequest("ya29.first", 3600, new AtomicReference<>());
        expectTokenRequest("ya29.second", 3600, new AtomicReference<>());

        // when & then
        assertThat(provider.accessToken()).isEqualTo("ya29.first");
        clock.advance(Duration.ofMinutes(30));
        assertThat(provider.accessToken()).isEqualTo("ya29.first");   // 요청 없이 재사용
        clock.advance(Duration.ofMinutes(30));                         // 만료 1분 전 안쪽
        assertThat(provider.accessToken()).isEqualTo("ya29.second");
        server.verify();
    }

    @Test
    @DisplayName("invalidate 하면 다음에 새로 받는다")
    void invalidate() {
        // given
        expectTokenRequest("ya29.first", 3600, new AtomicReference<>());
        expectTokenRequest("ya29.second", 3600, new AtomicReference<>());
        provider.accessToken();

        // when
        provider.invalidate();

        // then
        assertThat(provider.accessToken()).isEqualTo("ya29.second");
    }

    @Test
    @DisplayName("토큰 서버가 토큰을 주지 않으면 예외")
    void missingToken() {
        // given
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> provider.accessToken()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("환경변수에 줄바꿈이 \\n 문자로 들어와도 개인 키를 읽는다")
    void parsesEscapedNewlines() {
        // given
        String escaped = pem(keys).replace("\n", "\\n");

        // when & then
        assertThat(FcmAccessTokenProvider.parsePrivateKey(escaped).getEncoded()).isEqualTo(keys.getPrivate().getEncoded());
    }

    @Test
    @DisplayName("개인 키 형식이 잘못되면 어떤 설정을 확인해야 하는지 알려 준다")
    void invalidPrivateKey() {
        assertThatThrownBy(() -> FcmAccessTokenProvider.parsePrivateKey("not-a-key"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("FCM_PRIVATE_KEY");
    }
}
