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
        REJECTED,
        /** AI 429: 요청 횟수 제한 */
        RATE_LIMITED,
        /** AI 404 INDEX_NOT_FOUND: AI가 서류를 찾지 못함 (분석을 다시 해야 함) */
        INDEX_NOT_FOUND,
        /** AI 422 NOT_COMPARABLE: 비교할 수 없는 서류 쌍 (reason에 이유) */
        NOT_COMPARABLE,
        /** AI 422 NO_DIFFERENCE: 차이가 없어 문자를 만들 필요가 없음 */
        NO_DIFFERENCE
    }

    private final Kind kind;

    /** AI가 준 세부 사유 (예: DIFFERENT_PROVIDER). 없으면 null */
    private String reason;

    public AiClientException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public AiClientException(Kind kind, String message) {
        this(kind, message, null);
    }

    public AiClientException withReason(String reason) {
        this.reason = reason;
        return this;
    }

    public boolean isRetryable() {
        return kind == Kind.SERVER_ERROR || kind == Kind.TIMEOUT;
    }
}
