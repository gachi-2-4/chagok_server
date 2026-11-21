package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM이 만료됐다고 알려 준 푸시 토큰을 지운다. 발송은 트랜잭션 밖(비동기)에서 하므로 지우기만 짧은 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class PushTokenCleaner {

    private final PushTokenRepository pushTokens;

    @Transactional
    public void removeInvalid(String fcmToken) {
        pushTokens.deleteByFcmToken(fcmToken);
    }
}
