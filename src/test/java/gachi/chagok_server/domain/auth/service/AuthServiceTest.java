package gachi.chagok_server.domain.auth.service;

import gachi.chagok_server.domain.auth.dto.request.LoginRequest;
import gachi.chagok_server.domain.auth.dto.request.LogoutRequest;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.ConsentHistoryRepository;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock PhoneVerificationService phones;
    @Mock TokenService tokens;
    @Mock ConsentHistoryRepository consents;
    @Mock NotificationSettingsRepository notificationSettings;
    @Mock PushTokenRepository pushTokens;
    @InjectMocks AuthService authService;

    @Test
    @DisplayName("로그아웃 시 푸시 토큰은 리프레시 세션 소유자의 기기만 삭제한다")
    void logoutDeletesOnlyOwnersPushToken() {
        // given
        LogoutRequest request = new LogoutRequest("refresh", "device-1");
        given(tokens.logout("refresh", "access")).willReturn(Optional.of(3L));

        // when
        authService.logout(request, "access");

        // then
        verify(pushTokens).deleteByDeviceIdAndUserId("device-1", 3L);
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰으로 로그아웃하면 푸시 토큰을 삭제하지 않는다")
    void logoutWithInvalidRefreshTokenKeepsPushToken() {
        // given
        LogoutRequest request = new LogoutRequest("forged", "victim-device");
        given(tokens.logout("forged", null)).willReturn(Optional.empty());

        // when
        authService.logout(request, null);

        // then
        verify(pushTokens, never()).deleteByDeviceIdAndUserId(anyString(), any());
        verify(pushTokens, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 예외가 발생한다")
    void loginWithWrongPassword() {
        // given
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        given(users.findByPhoneNumber("01012345678")).willReturn(Optional.of(user));
        given(encoder.matches("wrong", "hash")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(new LoginRequest("01012345678", "wrong", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("탈퇴한 사용자는 로그인할 수 없다")
    void loginWithDeletedUser() {
        // given
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        user.deactivate();
        given(users.findByPhoneNumber("01012345678")).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authService.login(new LoginRequest("01012345678", "pw", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
