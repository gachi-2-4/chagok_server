package gachi.chagok_server.domain.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 문자를 보내지 않고 인증번호를 서버 로그에만 남기는 발송기. 시연·개발용이다.
 * auth.sms.provider=log 일 때만 활성화되며, 운영 환경에서는 사용하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.sms.provider", havingValue = "log")
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phoneNumber, String message) {
        log.warn("[SMS 모의 발송] 실제 문자는 전송되지 않습니다. to={}, message={}", phoneNumber, message);
    }
}
