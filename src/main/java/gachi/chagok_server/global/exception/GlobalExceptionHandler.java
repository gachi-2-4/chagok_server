package gachi.chagok_server.global.exception;

import gachi.chagok_server.global.common.ApiResponse;
import gachi.chagok_server.global.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리기. 모든 예외 응답은 ApiResponse로 감싸서 반환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return toResponse(errorCode, ErrorResponse.of(errorCode));
    }

    // MethodArgumentNotValidException(@Valid @RequestBody)도 BindException의 하위 타입이라 함께 처리된다.
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getFieldErrors()) {
            String message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "유효하지 않은 값입니다.";
            errors.putIfAbsent(fieldError.getField(), message);
        }
        return toResponse(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return toResponse(ErrorCode.INVALID_TYPE, ErrorResponse.of(ErrorCode.INVALID_TYPE));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        String message = e.getMessage() != null ? e.getMessage() : ErrorCode.INVALID_INPUT.getMessage();
        return toResponse(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }

    // 요청 본문 파싱 오류는 내부 구조가 드러날 수 있으므로 상세 메시지를 노출하지 않는다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return toResponse(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    // 없는 URL 요청이 아래 Exception 핸들러에 걸려 500이 되지 않도록 404로 처리한다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND, ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 업로드 크기 제한(spring.servlet.multipart)을 넘은 경우
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.FILE_TOO_LARGE, ErrorResponse.of(ErrorCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        String message = e.getMessage() != null ? e.getMessage() : ErrorCode.CONFLICT.getMessage();
        return toResponse(ErrorCode.CONFLICT, ErrorResponse.of(ErrorCode.CONFLICT, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, ErrorResponse errorResponse) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorResponse));
    }
}
