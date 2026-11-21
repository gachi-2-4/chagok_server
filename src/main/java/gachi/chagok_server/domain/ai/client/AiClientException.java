package gachi.chagok_server.domain.ai.client;

import lombok.Getter;

/**
 * AI 서버 호출 실패. kind로 BE가 어떻게 처리할지(실패 사유 기록·재시도) 정한다.
 */
@Getter
public class AiClientException extends RuntimeException {

    public enum Kind {
        /** AI 422: 글자를 인식하지 못함 (재시도해도 소용없음) */
        TEXT_NOT_RECOGNIZED,
        /** AI 5xx·연결 실패 (한 번 더 시도할 수 있음) */
        SERVER_ERROR,
        /** 응답 시간 초과 (한 번 더 시도할 수 있음) */
        TIMEOUT,
        /** 그 밖의 4xx: 요청이 잘못됨 (재시도해도 소용없음) */
        REJECTED
    }

    private final Kind kind;

    public AiClientException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public AiClientException(Kind kind, String message) {
        this(kind, message, null);
    }

    public boolean isRetryable() {
        return kind == Kind.SERVER_ERROR || kind == Kind.TIMEOUT;
    }
}
