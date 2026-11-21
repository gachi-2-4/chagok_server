package gachi.chagok_server.domain.auth.service;

import gachi.chagok_server.domain.auth.oauth.GoogleOAuthClient;
import gachi.chagok_server.domain.auth.oauth.KakaoOAuthClient;
import gachi.chagok_server.domain.auth.oauth.OAuthUserInfo;
import gachi.chagok_server.domain.user.entity.SocialAccount;
import gachi.chagok_server.domain.user.entity.SocialProvider;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.SocialAccountRepository;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock KakaoOAuthClient kakao;
    @Mock GoogleOAuthClient google;
    @Mock UserRepository users;
    @Mock SocialAccountRepository social;
    @Mock TokenService tokens;
    @InjectMocks OAuthService oAuthService;

    @Test
    @DisplayName("탈퇴한 사용자와 연결된 소셜 계정으로는 토큰을 발급하지 않는다")
    void loginWithDeletedLinkedUser() {
        // given
        User user = User.socialUser("홍길동");
        user.deactivate();
        given(kakao.verify("kakao-token")).willReturn(new OAuthUserInfo("k-1", "홍길동", null, null));
        given(social.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-1"))
                .willReturn(Optional.of(new SocialAccount(user, SocialProvider.KAKAO, "k-1")));

        // when & then
        assertThatThrownBy(() -> oAuthService.kakaoLogin("kakao-token", true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(tokens, never()).issue(any(), anyBoolean());
    }
}
