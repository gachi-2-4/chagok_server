package gachi.chagok_server.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외.
 * 그대로 사용하거나(new BusinessException(ErrorCode.X)), 도메인별로 상속해 사용한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
