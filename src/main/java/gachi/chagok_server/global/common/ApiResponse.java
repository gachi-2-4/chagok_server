package gachi.chagok_server.global.common;

/**
 * 모든 API 응답을 감싸는 공통 래퍼.
 * 성공 시 data, 실패 시 error가 채워진다.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {

    // 레코드 컴포넌트 success()와 이름이 겹치지 않도록 ok로 명명한다.
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
