package gachi.chagok_server.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * FCM 연동 전까지 쓰는 기본 구현. 실제로 보내지 않고 로그만 남긴다.
 * 다른 {@link PushSender} 빈이 등록되면 자동으로 물러난다.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(value = PushSender.class, ignored = LoggingPushSender.class)
public class LoggingPushSender implements PushSender {

    @Override
    public void send(PushMessage message) {
        // 토큰은 민감한 값이라 로그에 남기지 않는다.
        log.info("푸시 발송(미연동): title={}, data={}", message.title(), message.data());
    }
}
