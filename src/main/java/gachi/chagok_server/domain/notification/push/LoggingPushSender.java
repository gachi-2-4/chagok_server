package gachi.chagok_server.domain.notification.push;

import lombok.extern.slf4j.Slf4j;

/**
 * FCM 설정이 없을 때 쓰는 구현. 실제로 보내지 않고 로그만 남긴다 (로컬 개발용).
 */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(PushMessage message) {
        // 토큰은 민감한 값이라 로그에 남기지 않는다.
        log.info("푸시 발송(FCM 미설정): title={}, data={}", message.title(), message.data());
        return PushResult.SENT;
    }
}
