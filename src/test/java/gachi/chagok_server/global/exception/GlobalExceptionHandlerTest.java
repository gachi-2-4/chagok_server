package gachi.chagok_server.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태 코드와 메시지로 응답한다")
    void handleBusinessException() throws Exception {
        // given
        String url = "/test/business";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.CONFLICT.getMessage()))
                .andExpect(jsonPath("$.error.errors").doesNotExist());
    }

    @Test
    @DisplayName("BindException은 400과 필드별 에러 메시지로 응답한다")
    void handleBindException() throws Exception {
        // given
        String url = "/test/bind";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.errors.name").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("경로 변수 타입이 맞지 않으면 400 INVALID_TYPE으로 응답한다")
    void handleMethodArgumentTypeMismatchException() throws Exception {
        // given
        String url = "/test/numbers/abc";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TYPE"));
    }

    @Test
    @DisplayName("IllegalArgumentException은 400과 예외 메시지로 응답한다")
    void handleIllegalArgumentException() throws Exception {
        // given
        String url = "/test/illegal-argument";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("잘못된 인자"));
    }

    @Test
    @DisplayName("읽을 수 없는 요청 본문은 상세 내용 없이 400으로 응답한다")
    void handleHttpMessageNotReadableException() throws Exception {
        // given
        String invalidJson = "{invalid";

        // when & then
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INVALID_INPUT.getMessage()));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405로 응답한다")
    void handleHttpRequestMethodNotSupportedException() throws Exception {
        // given
        String url = "/test/business";

        // when & then
        mockMvc.perform(put(url))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404로 응답한다")
    void handleNoResourceFoundException() throws Exception {
        // given
        String url = "/not-exists";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("IllegalStateException은 409와 예외 메시지로 응답한다")
    void handleIllegalStateException() throws Exception {
        // given
        String url = "/test/illegal-state";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("잘못된 상태"));
    }

    @Test
    @DisplayName("처리되지 않은 예외는 내부 메시지를 숨기고 500으로 응답한다")
    void handleException() throws Exception {
        // given
        String url = "/test/unexpected";

        // when & then
        mockMvc.perform(get(url))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        @GetMapping("/test/bind")
        void bind() throws BindException {
            BindException exception = new BindException(new Object(), "request");
            exception.addError(new org.springframework.validation.FieldError("request", "name", "이름은 필수입니다."));
            throw exception;
        }

        @GetMapping("/test/numbers/{id}")
        void number(@PathVariable Long id) {
        }

        @GetMapping("/test/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("잘못된 인자");
        }

        @PostMapping("/test/body")
        void body(@RequestBody Map<String, String> body) {
        }

        @GetMapping("/test/illegal-state")
        void illegalState() {
            throw new IllegalStateException("잘못된 상태");
        }

        @GetMapping("/test/unexpected")
        void unexpected() throws Exception {
            throw new Exception("내부 정보");
        }
    }
}
