package gachi.chagok_server.domain.auth.service;

import gachi.chagok_server.domain.auth.entity.RefreshSession;
import gachi.chagok_server.domain.auth.repository.RefreshSessionRepository;
import gachi.chagok_server.domain.auth.repository.RevokedAccessTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock JwtTokenProvider jwt;
    @Mock RefreshSessionRepository sessions;
    @Mock RevokedAccessTokenRepository revoked;
    @InjectMocks TokenService tokenService;

    private User user(long id) {
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("존재하지 않는 리프레시 토큰으로 재발급하면 401 예외가 발생한다")
    void refreshWithUnknownToken() {
        // given
        given(sessions.findLockedByTokenHash(any())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.refresh("unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 리프레시 토큰은 재발급되지 않고 세션이 삭제된다")
    void refreshForDeletedUser() {
        // given
        User user = user(1L);
        user.deactivate();
        RefreshSession session = new RefreshSession(user, "h", Instant.now().plusSeconds(600), true);
        given(sessions.findLockedByTokenHash(any())).willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> tokenService.refresh("token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(sessions).delete(session);
        verify(jwt, never()).issue(anyLong());
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 재발급되지 않고 세션이 삭제된다")
    void refreshWithExpiredSession() {
        // given
        RefreshSession session = new RefreshSession(user(1L), "h", Instant.now().minusSeconds(1), true);
        given(sessions.findLockedByTokenHash(any())).willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> tokenService.refresh("token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(sessions).delete(session);
    }

    @Test
    @DisplayName("로그아웃하면 세션을 삭제하고 세션 소유자의 ID를 반환한다")
    void logoutReturnsSessionOwner() {
        // given
        RefreshSession session = new RefreshSession(user(7L), "h", Instant.now().plusSeconds(600), true);
        given(sessions.findByTokenHash(TokenService.hash("refresh"))).willReturn(Optional.of(session));

        // when
        Optional<Long> result = tokenService.logout("refresh", null);

        // then
        assertThat(result).contains(7L);
        verify(sessions).delete(session);
    }

    @Test
    @DisplayName("존재하지 않는 리프레시 토큰으로 로그아웃하면 사용자 ID가 비어 있다")
    void logoutWithUnknownToken() {
        // given
        given(sessions.findByTokenHash(any())).willReturn(Optional.empty());

        // when
        Optional<Long> result = tokenService.logout("unknown", null);

        // then
        assertThat(result).isEmpty();
    }
}
