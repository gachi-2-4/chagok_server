package gachi.chagok_server.domain.document.controller;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.UploadResponse;
import gachi.chagok_server.domain.document.entity.AnalysisStep;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.service.DocumentCommandService;
import gachi.chagok_server.domain.document.service.DocumentFieldService;
import gachi.chagok_server.domain.document.service.DocumentQueryService;
import gachi.chagok_server.domain.document.service.DocumentUploadService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, InternalKeyVerifier.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "chagok.internal.key=internal-test-key"
})
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean DocumentUploadService uploadService;
    @MockitoBean DocumentQueryService queryService;
    @MockitoBean DocumentCommandService commandService;
    @MockitoBean DocumentFieldService fieldService;
    @MockitoBean TokenService tokenService;
    @MockitoBean UserRepository userRepository;

    private String bearer() {
        String token = jwtTokenProvider.issue(1L);
        given(tokenService.isRevoked(token)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.phoneUser("01012345678", "hash", "홍길동")));
        return "Bearer " + token;
    }

    @Test
    @DisplayName("AI 서버는 내부 키와 userId로 서류를 가져가고, 이때는 원문을 담은 내부용 조회를 쓴다")
    void internalReadWithKey() throws Exception {
        // when
        mockMvc.perform(get("/api/v1/documents/520").param("userId", "1024").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // then
        verify(queryService).internalDetail(1024L, 520L);
    }

    @Test
    @DisplayName("내부 키가 틀리면 403")
    void internalReadWithWrongKey() throws Exception {
        mockMvc.perform(get("/api/v1/documents/520").param("userId", "1").header("X-Internal-Key", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("내부 호출에 userId가 없으면 400")
    void internalReadWithoutUserId() throws Exception {
        mockMvc.perform(get("/api/v1/documents/520").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("내부 키로는 서류 상세 조회 외의 API를 호출할 수 없다")
    void internalKeyDoesNotOpenOtherApis() throws Exception {
        mockMvc.perform(get("/api/v1/documents").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/documents/520").header("X-Internal-Key", "internal-test-key"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(queryService, commandService);
    }

    @Test
    @DisplayName("토큰도 내부 키도 없으면 401")
    void withoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/documents/520"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("앱은 액세스 토큰으로 서류 상세를 조회한다 (원문 없는 일반 조회)")
    void appRead() throws Exception {
        mockMvc.perform(get("/api/v1/documents/520").header("Authorization", bearer()))
                .andExpect(status().isOk());

        verify(queryService).detail(1L, 520L);
    }

    @Test
    @DisplayName("서류를 올리면 202와 분석 중 상태를 돌려준다")
    void upload() throws Exception {
        // given
        given(uploadService.upload(eq(1L), any(), eq(1L), any(), any(), any())).willReturn(
                new UploadResponse(520L, "새 서류", null, new gachi.chagok_server.domain.document.dto.DocumentDtos.BoxRef(1L, "집 · 임대"),
                        DocumentStatus.ANALYZING, AnalysisStep.ENHANCING, 1, null));
        MockMultipartFile file = new MockMultipartFile("files", "page1.jpg", "image/jpeg", new byte[]{1, 2, 3});

        // when & then
        mockMvc.perform(multipart("/api/v1/documents").file(file).param("boxId", "1").param("source", "CAMERA")
                        .header("Authorization", bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.documentId").value(520))
                .andExpect(jsonPath("$.data.status").value("ANALYZING"))
                .andExpect(jsonPath("$.data.analysisStep").value("ENHANCING"));
    }

    @Test
    @DisplayName("업로드 검사에서 걸린 오류는 공통 에러 형식으로 돌려준다")
    void uploadRejected() throws Exception {
        // given
        given(uploadService.upload(any(), any(), any(), any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.ENCRYPTED_PDF));
        MockMultipartFile file = new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[]{1});

        // when & then
        mockMvc.perform(multipart("/api/v1/documents").file(file).param("boxId", "1").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ENCRYPTED_PDF"));
    }

    @Test
    @DisplayName("서류를 지우면 204")
    void deleteDocument() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/520").header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        verify(commandService).delete(1L, 520L);
    }
}
