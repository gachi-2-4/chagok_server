package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.entity.Notification;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.push.PushMessage;
import gachi.chagok_server.domain.notification.push.PushSender;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 알림 목록에 알림을 쌓고 푸시를 보낸다. 푸시를 못 받는 상태여도 목록에는 항상 쌓인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final PushTokenRepository pushTokens;
    private final PushSender pushSender;

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
            push(user.getId(), notification);
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

    private void push(Long userId, Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType().name());
        if (notification.getDocumentId() != null) {
            data.put("documentId", String.valueOf(notification.getDocumentId()));
        }
        for (var token : pushTokens.findByUserIdAndPermissionGrantedTrue(userId)) {
            try {
                pushSender.send(new PushMessage(token.getFcmToken(), notification.getTitle(), notification.getMessage(), data));
            } catch (RuntimeException e) {
                log.warn("푸시 발송 실패: notificationId={}", notification.getId(), e);
            }
        }
    }
}
