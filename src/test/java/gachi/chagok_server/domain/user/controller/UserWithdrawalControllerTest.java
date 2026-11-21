package gachi.chagok_server.domain.user.controller;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.domain.user.service.UserService;
import gachi.chagok_server.domain.user.service.UserWithdrawalService;
import gachi.chagok_server.global.security.JwtTokenProvider;
import gachi.chagok_server.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class UserWithdrawalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserService userService;
    @MockitoBean UserWithdrawalService withdrawalService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    @Test
    @DisplayName("탈퇴하면 Body 없이 204를 돌려준다")
    void withdraw() throws Exception {
        // given
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));

        // when & then
        mockMvc.perform(delete("/api/v1/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(withdrawalService).withdraw(1L);
    }

    @Test
    @DisplayName("토큰이 없으면 401이고 탈퇴하지 않는다")
    void withoutToken() throws Exception {
        mockMvc.perform(delete("/api/v1/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(withdrawalService);
    }

    @Test
    @DisplayName("이미 탈퇴한 사용자의 토큰은 인증되지 않는다")
    void withdrawnUserTokenRejected() throws Exception {
        // given
        String token = jwtTokenProvider.issue(1L);
        User gone = User.phoneUser("01012345678", "hash", "홍길동");
        gone.withdraw();
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(gone));

        // when & then
        mockMvc.perform(delete("/api/v1/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(withdrawalService);
    }
}
