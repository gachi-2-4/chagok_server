package gachi.chagok_server.domain.auth.oauth;

import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoOAuthClientTest {

    private static final long APP_ID = 1234L;
    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(builder, APP_ID);
    }

    @Test
    @DisplayName("우리 앱에서 발급된 토큰이면 카카오 사용자 정보를 반환한다")
    void verifyWithOwnAppToken() {
        // given
        server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"id\":42,\"expires_in\":3600,\"app_id\":1234}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("{\"id\":42,\"kakao_account\":{\"name\":\"홍길동\",\"phone_number\":\"+82 10-1234-5678\"}}", MediaType.APPLICATION_JSON));

        // when
        OAuthUserInfo info = client.verify("token");

        // then
        assertThat(info.providerUserId()).isEqualTo("42");
        assertThat(info.name()).isEqualTo("홍길동");
        assertThat(info.phoneNumber()).isEqualTo("01012345678");
        server.verify();
    }

    @Test
    @DisplayName("다른 앱에서 발급된 토큰이면 401 예외가 발생하고 사용자 정보를 조회하지 않는다")
    void verifyWithOtherAppToken() {
        // given
        server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
                .andRespond(withSuccess("{\"id\":42,\"expires_in\":3600,\"app_id\":9999}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.verify("token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        server.verify();
    }

    @Test
    @DisplayName("카카오가 토큰을 거부하면 500이 아닌 401 예외가 발생한다")
    void verifyWithInvalidToken() {
        // given
        server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> client.verify("invalid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
