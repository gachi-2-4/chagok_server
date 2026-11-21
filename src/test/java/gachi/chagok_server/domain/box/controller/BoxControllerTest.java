package gachi.chagok_server.domain.box.controller;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.box.dto.BoxDtos.Created;
import gachi.chagok_server.domain.box.dto.BoxDtos.CreateRequest;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.service.BoxService;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoxController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class BoxControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean BoxService boxService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    private String bearer() {
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));
        return "Bearer " + token;
    }

    @Test
    @DisplayName("박스를 만들면 201과 만든 박스를 돌려준다")
    void create() throws Exception {
        // given
        given(boxService.create(eq(1L), any(CreateRequest.class)))
                .willReturn(new Created(6L, "반려동물", BoxColor.GREEN, BoxIcon.PET, 0, null, null));

        // when & then
        mockMvc.perform(post("/api/v1/boxes").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"반려동물\",\"color\":\"GREEN\",\"icon\":\"PET\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.boxId").value(6))
                .andExpect(jsonPath("$.data.color").value("GREEN"))
                .andExpect(jsonPath("$.data.documentCount").value(0));
    }

    @Test
    @DisplayName("색·아이콘이 코드표에 없는 값이면 400")
    void createWithUnknownColor() throws Exception {
        mockMvc.perform(post("/api/v1/boxes").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"반려동물\",\"color\":\"RED\",\"icon\":\"PET\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(boxService);
    }

    @Test
    @DisplayName("필수 값이 빠지면 400")
    void createWithoutRequired() throws Exception {
        mockMvc.perform(post("/api/v1/boxes").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"반려동물\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("없는 박스는 공통 에러 형식의 404")
    void detailNotFound() throws Exception {
        // given
        given(boxService.open(1L, 9L)).willThrow(new BusinessException(ErrorCode.BOX_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/boxes/9").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BOX_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("박스를 찾을 수 없어요."));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/boxes")).andExpect(status().isUnauthorized());
    }
}
