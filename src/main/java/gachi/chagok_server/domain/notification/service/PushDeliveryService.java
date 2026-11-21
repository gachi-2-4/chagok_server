package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.push.PushMessage;
import gachi.chagok_server.domain.notification.push.PushResult;
import gachi.chagok_server.domain.notification.push.PushSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 푸시를 비동기로 보낸다. FCM 호출이 느려도 알림 저장·스케줄러·서류 분석 처리를 붙잡지 않는다.
 * 토큰이 만료됐다고 하면(UNREGISTERED) 그 토큰을 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private final PushSender pushSender;
    private final PushTokenCleaner tokenCleaner;

    @Async
    public void deliver(List<PushMessage> messages) {
        for (PushMessage message : messages) {
            try {
                if (pushSender.send(message) == PushResult.TOKEN_INVALID) {
                    tokenCleaner.removeInvalid(message.fcmToken());
                }
            } catch (RuntimeException e) {
                // 한 기기의 실패가 다른 기기로 보내는 것을 막지 않는다.
                log.warn("푸시 발송 처리 중 오류: {}", e.toString());
            }
        }
    }
}
