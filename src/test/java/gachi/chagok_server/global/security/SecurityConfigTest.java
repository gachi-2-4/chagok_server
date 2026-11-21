package gachi.chagok_server.global.security;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.user.controller.InternalUserController;
import gachi.chagok_server.domain.user.controller.UserController;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InternalUserController.class, UserController.class})
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserService userService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    @Test
    @DisplayName("내부 조회 API는 JWT 없이 올바른 내부 키만으로 호출할 수 있다")
    void internalApiWithValidKey() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/user/1").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("내부 키가 틀리면 공통 응답 형식의 403을 반환한다")
    void internalApiWithWrongKey() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/user/1").header("X-Internal-Key", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("내 정보 조회는 토큰이 없으면 401을 반환한다")
    void meWithoutToken() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내 정보 조회는 유효한 액세스 토큰이 있으면 통과한다")
    void meWithValidToken() throws Exception {
        // given
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));
        given(userService.me(any())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("폐기된 액세스 토큰으로는 인증되지 않는다")
    void meWithRevokedToken() throws Exception {
        // given
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/v1/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
