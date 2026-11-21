package gachi.chagok_server.domain.notification.push;

/**
 * 푸시 한 건의 발송 결과.
 */
public enum PushResult {
    /** 발송을 요청했다 */
    SENT,
    /** 토큰이 만료·해지돼 이 토큰으로는 보낼 수 없다 (FCM UNREGISTERED). 서버가 토큰을 지운다. */
    TOKEN_INVALID,
    /** 그 밖의 이유로 보내지 못했다 (일시 오류 등). 토큰은 그대로 둔다. */
    FAILED
}
