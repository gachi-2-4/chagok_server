package gachi.chagok_server.domain.comparison.controller;

import gachi.chagok_server.domain.ai.dto.AiComparisonDtos.InquiryMessage;
import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.CompareRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Response;
import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.ComparisonStatus;
import gachi.chagok_server.domain.comparison.service.ComparisonService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComparisonController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, InternalKeyVerifier.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class ComparisonControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean ComparisonService comparisonService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    private String bearer() {
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));
        return "Bearer " + token;
    }

    private static Response response() {
        return new Response(77L, CompareType.BILL, ComparisonStatus.MISMATCH, "약속과 다른 금액이 있어요", null, null,
                Map.of("amount", 12000), List.of(), List.of(), "추정 결과이며 확인이 필요해요.", false, null);
    }

    @Test
    @DisplayName("서류를 비교하면 201과 비교 결과를 돌려준다")
    void compare() throws Exception {
        // given
        given(comparisonService.compare(eq(1L), any(CompareRequest.class))).willReturn(response());

        // when & then
        mockMvc.perform(post("/api/v1/comparisons").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDocumentId\":488,\"targetDocumentId\":533,\"compareType\":\"BILL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comparisonId").value(77))
                .andExpect(jsonPath("$.data.status").value("MISMATCH"))
                .andExpect(jsonPath("$.data.highlight.amount").value(12000))
                .andExpect(jsonPath("$.data.outdated").value(false));
    }

    @Test
    @DisplayName("비교 종류가 코드표에 없거나 서류 ID가 빠지면 400")
    void compareInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/comparisons").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDocumentId\":488,\"targetDocumentId\":533,\"compareType\":\"ETC\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/comparisons").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compareType\":\"BILL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(comparisonService);
    }

    @Test
    @DisplayName("비교할 수 없는 서류는 이유(reason)와 함께 422")
    void notComparable() throws Exception {
        // given
        given(comparisonService.compare(any(), any())).willThrow(new BusinessException(ErrorCode.NOT_COMPARABLE, "DIFFERENT_PROVIDER"));

        // when & then
        mockMvc.perform(post("/api/v1/comparisons").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDocumentId\":488,\"targetDocumentId\":533,\"compareType\":\"BILL\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("NOT_COMPARABLE"))
                .andExpect(jsonPath("$.error.reason").value("DIFFERENT_PROVIDER"));
    }

    @Test
    @DisplayName("AI 서버는 내부 키와 userId로 비교 결과를 가져간다")
    void internalRead() throws Exception {
        mockMvc.perform(get("/api/v1/comparisons/77").param("userId", "1024").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isOk());

        verify(comparisonService).get(1024L, 77L);
    }

    @Test
    @DisplayName("내부 키가 틀리면 403, 토큰도 키도 없으면 401")
    void readRejected() throws Exception {
        mockMvc.perform(get("/api/v1/comparisons/77").param("userId", "1").header("X-Internal-Key", "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/comparisons/77")).andExpect(status().isUnauthorized());

        verifyNoInteractions(comparisonService);
    }

    @Test
    @DisplayName("앱은 토큰으로 비교 결과를 다시 연다")
    void appRead() throws Exception {
        // given
        given(comparisonService.get(1L, 77L)).willReturn(response());

        // when & then
        mockMvc.perform(get("/api/v1/comparisons/77").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comparisonId").value(77));
    }

    @Test
    @DisplayName("확인 요청 문자를 만들면 201, Body 없이도 만들 수 있다")
    void inquiryMessage() throws Exception {
        // given
        given(comparisonService.inquiryMessage(eq(1L), eq(77L), any())).willReturn(
                new InquiryMessage(77L, Map.of("text", "안녕하세요.", "type", "SMS", "byteLength", 11), "POLITE"));

        // when & then
        mockMvc.perform(post("/api/v1/comparisons/77/inquiry-messages").header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.message.type").value("SMS"))
                .andExpect(jsonPath("$.data.tone").value("POLITE"));
    }

    @Test
    @DisplayName("어조가 코드표에 없으면 400")
    void inquiryInvalidTone() throws Exception {
        mockMvc.perform(post("/api/v1/comparisons/77/inquiry-messages").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tone\":\"ANGRY\"}"))
                .andExpect(status().isBadRequest());
    }
}
