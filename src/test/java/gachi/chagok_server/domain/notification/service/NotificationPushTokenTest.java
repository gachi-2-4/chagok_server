package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.dto.NotificationDtos.PushRequest;
import gachi.chagok_server.domain.notification.entity.PushToken;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPushTokenTest {

    private static final String DEVICE_ID = "8d1c3e0a-6b2f-4c1e-9a77-1f0c2b3d4e5f";

    @Mock NotificationRepository notifications;
    @Mock NotificationSettingsRepository settings;
    @Mock PushTokenRepository pushTokens;
    @Mock UserRepository users;
    @Mock UserService userService;

    @Test
    @DisplayName("푸시 토큰을 등록하면 같은 FCM 토큰이 다른 기기·사용자에게 있는 것을 지운다")
    void registerRemovesSameTokenOnOtherDevices() {
        // given
        NotificationService service = new NotificationService(notifications, settings, pushTokens, users, userService);
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        given(users.findById(1L)).willReturn(Optional.of(user));
        given(pushTokens.findById(DEVICE_ID)).willReturn(Optional.empty());

        // when
        service.pushToken(1L, DEVICE_ID, new PushRequest("fcm-token", "IOS", true, "1.0.0"));

        // then
        ArgumentCaptor<PushToken> saved = ArgumentCaptor.forClass(PushToken.class);
        verify(pushTokens).save(saved.capture());
        assertThat(saved.getValue().getFcmToken()).isEqualTo("fcm-token");
        verify(pushTokens).deleteSameTokenOnOtherDevices("fcm-token", DEVICE_ID);
    }
}
