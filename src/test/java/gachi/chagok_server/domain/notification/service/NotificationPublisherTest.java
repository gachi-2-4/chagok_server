package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.entity.Notification;
import gachi.chagok_server.domain.notification.entity.NotificationSettings;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.entity.PushToken;
import gachi.chagok_server.domain.notification.push.PushMessage;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    private static final Long USER_ID = 1L;

    @Mock NotificationRepository notifications;
    @Mock NotificationSettingsRepository settings;
    @Mock PushTokenRepository pushTokens;
    @Mock PushDeliveryService delivery;

    private NotificationPublisher publisher;
    private User user;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(notifications, settings, pushTokens, delivery);
        user = User.phoneUser("01012345678", "hash", "홍길동");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(notifications.save(any(Notification.class))).willAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3010L);
            return saved;
        });
    }

    private PushToken token(String deviceId, String fcmToken) {
        PushToken token = new PushToken(deviceId, user);
        token.update(user, fcmToken, "IOS", true, "1.0");
        return token;
    }

    @Test
    @DisplayName("권한을 허용한 모든 기기로 보낼 푸시를 만든다 (data에 알림 ID·종류·서류 ID)")
    void pushesToAllGrantedDevices() {
        // given
        given(pushTokens.findByUserIdAndPermissionGrantedTrue(USER_ID)).willReturn(List.of(token("d1", "fcm-1"), token("d2", "fcm-2")));

        // when
        publisher.publish(user, NotificationType.ANALYSIS_COMPLETED, "분석이 끝났어요", "확인해 주세요.",
                n -> n.withDocument(501L, "임대차계약서", "집 · 임대"));

        // then
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(delivery).deliver(captor.capture());
        assertThat(captor.getValue()).extracting(PushMessage::fcmToken).containsExactly("fcm-1", "fcm-2");
        assertThat(captor.getValue().get(0).title()).isEqualTo("분석이 끝났어요");
        assertThat(captor.getValue().get(0).data()).containsEntry("notificationId", "3010")
                .containsEntry("type", "ANALYSIS_COMPLETED").containsEntry("documentId", "501");
    }

    @Test
    @DisplayName("서류와 연결되지 않은 알림의 data에는 documentId가 없다")
    void withoutDocument() {
        // given
        given(pushTokens.findByUserIdAndPermissionGrantedTrue(USER_ID)).willReturn(List.of(token("d1", "fcm-1")));

        // when
        publisher.publish(user, NotificationType.ANALYSIS_FAILED, "실패", "다시 올려 주세요.", null);

        // then
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(delivery).deliver(captor.capture());
        assertThat(captor.getValue().get(0).data()).doesNotContainKey("documentId");
    }

    @Test
    @DisplayName("비교 완료 알림은 목록에만 쌓고 푸시를 보내지 않는다")
    void comparisonCompletedHasNoPush() {
        // when
        Notification notification = publisher.publish(user, NotificationType.COMPARISON_COMPLETED, "비교 완료", "확인해 보세요.", null);

        // then
        assertThat(notification.getReadAt()).isNotNull();
        verifyNoInteractions(pushTokens, delivery);
    }

    @Test
    @DisplayName("기한 알림 설정이 꺼져 있으면 목록에만 쌓고 푸시는 보내지 않는다")
    void deadlineAlertDisabled() {
        // given
        NotificationSettings off = new NotificationSettings(user);
        off.change(false, null);
        given(settings.findById(USER_ID)).willReturn(Optional.of(off));

        // when
        publisher.publish(user, NotificationType.DEADLINE_DUE, "기한", "내용", null);

        // then
        verify(notifications).save(any(Notification.class));
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("기한 알림 설정이 켜져 있거나 설정 행이 없으면 푸시를 보낸다")
    void deadlineAlertEnabledByDefault() {
        // given
        given(settings.findById(USER_ID)).willReturn(Optional.empty());
        given(pushTokens.findByUserIdAndPermissionGrantedTrue(USER_ID)).willReturn(List.of(token("d1", "fcm-1")));

        // when
        publisher.publish(user, NotificationType.DEADLINE_DUE, "기한", "내용", null);

        // then
        verify(delivery).deliver(any());
    }

    @Test
    @DisplayName("푸시를 받을 기기가 없어도 알림은 목록에 쌓이고 발송은 요청하지 않는다")
    void noDevices() {
        // given
        given(pushTokens.findByUserIdAndPermissionGrantedTrue(USER_ID)).willReturn(List.of());

        // when
        publisher.publish(user, NotificationType.ANALYSIS_COMPLETED, "분석 완료", "내용", null);

        // then
        verify(notifications).save(any(Notification.class));
        verify(delivery, never()).deliver(any());
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋된 뒤에 발송을 요청한다 (롤백되면 보내지 않는다)")
    void sendsOnlyAfterCommit() {
        // given
        given(pushTokens.findByUserIdAndPermissionGrantedTrue(USER_ID)).willReturn(List.of(token("d1", "fcm-1")));
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            publisher.publish(user, NotificationType.ANALYSIS_COMPLETED, "분석 완료", "내용", null);

            // then: 커밋 전에는 발송 요청이 없다
            verifyNoInteractions(delivery);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(delivery).deliver(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("롤백되면 발송을 요청하지 않는다")
    void noPushOnRollback() {
        // given
        given(pushTokens.findByUserIdAndPermissionGrantedTrue(USER_ID)).willReturn(List.of(token("d1", "fcm-1")));
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            publisher.publish(user, NotificationType.ANALYSIS_COMPLETED, "분석 완료", "내용", null);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // then
            verifyNoInteractions(delivery);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
