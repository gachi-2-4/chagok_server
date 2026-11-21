package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.entity.Notification;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.push.PushMessage;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 알림 목록에 알림을 쌓고 푸시를 보낸다. 푸시를 못 받는 상태여도 목록에는 항상 쌓인다.
 * 푸시는 알림 저장이 커밋된 뒤에 비동기로 보낸다 (롤백된 알림의 푸시가 나가지 않고, FCM 호출이 트랜잭션을 붙잡지 않도록).
 */
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final PushTokenRepository pushTokens;
    private final PushDeliveryService delivery;

    /**
     * @param link 알림에 서류·기한·비교 정보를 붙이는 함수 (없으면 null)
     */
    @Transactional
    public Notification publish(User user, NotificationType type, String title, String message, Consumer<Notification> link) {
        Notification notification = new Notification(user, type, title, message);
        if (link != null) {
            link.accept(notification);
        }
        notifications.save(notification);

        if (shouldPush(user.getId(), type)) {
            List<PushMessage> pushes = pushMessages(user.getId(), notification);
            if (!pushes.isEmpty()) {
                afterCommit(() -> delivery.deliver(pushes));
            }
        }
        return notification;
    }

    /** 비교 완료 알림은 푸시를 보내지 않고, 기한 알림은 알림 설정이 꺼져 있으면 푸시를 보내지 않는다. */
    private boolean shouldPush(Long userId, NotificationType type) {
        if (type == NotificationType.COMPARISON_COMPLETED) {
            return false;
        }
        if (type == NotificationType.DEADLINE_DUE) {
            return settings.findById(userId).map(s -> s.isDeadlineAlertEnabled()).orElse(true);
        }
        return true;
    }

    /** 알림을 받을 수 있는(기기 권한 허용) 모든 기기로 보낼 푸시. data는 앱이 알림을 눌렀을 때 바로 화면을 열 수 있는 값이다. */
    private List<PushMessage> pushMessages(Long userId, Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType().name());
        if (notification.getDocumentId() != null) {
            data.put("documentId", String.valueOf(notification.getDocumentId()));
        }
        return pushTokens.findByUserIdAndPermissionGrantedTrue(userId).stream()
                .map(token -> new PushMessage(token.getFcmToken(), notification.getTitle(), notification.getMessage(), data))
                .toList();
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
