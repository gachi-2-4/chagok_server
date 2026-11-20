package gachi.chagok_server.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import gachi.chagok_server.global.exception.ErrorCode;

import java.util.Map;

/**
 * ApiResponse의 error 필드에 담기는 에러 정보.
 *
 * @param code    에러 코드 이름 (예: INVALID_INPUT)
 * @param message 사용자에게 보여줄 메시지
 * @param errors  필드 검증 실패 시 필드명 → 메시지 (검증 오류가 아니면 생략)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Map<String, String> errors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, Map<String, String> errors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errors);
    }
}
