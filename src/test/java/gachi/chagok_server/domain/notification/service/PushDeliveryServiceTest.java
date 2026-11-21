package gachi.chagok_server.domain.notification.service;

import gachi.chagok_server.domain.notification.push.PushMessage;
import gachi.chagok_server.domain.notification.push.PushResult;
import gachi.chagok_server.domain.notification.push.PushSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushDeliveryServiceTest {

    @Mock PushSender pushSender;
    @Mock PushTokenCleaner tokenCleaner;
    @InjectMocks PushDeliveryService delivery;

    private static PushMessage message(String token) {
        return new PushMessage(token, "제목", "본문", Map.of());
    }

    @Test
    @DisplayName("만료된 토큰(TOKEN_INVALID)은 지우고, 보낸 토큰과 일시 실패한 토큰은 그대로 둔다")
    void removesOnlyInvalidTokens() {
        // given
        PushMessage ok = message("ok");
        PushMessage expired = message("expired");
        PushMessage failed = message("failed");
        given(pushSender.send(ok)).willReturn(PushResult.SENT);
        given(pushSender.send(expired)).willReturn(PushResult.TOKEN_INVALID);
        given(pushSender.send(failed)).willReturn(PushResult.FAILED);

        // when
        delivery.deliver(List.of(ok, expired, failed));

        // then
        verify(tokenCleaner).removeInvalid("expired");
        verify(tokenCleaner, never()).removeInvalid("ok");
        verify(tokenCleaner, never()).removeInvalid("failed");
    }

    @Test
    @DisplayName("한 기기에서 예외가 나도 나머지 기기로 계속 보낸다")
    void continuesAfterException() {
        // given
        PushMessage broken = message("broken");
        PushMessage next = message("next");
        given(pushSender.send(broken)).willThrow(new IllegalStateException("boom"));
        given(pushSender.send(next)).willReturn(PushResult.SENT);

        // when
        delivery.deliver(List.of(broken, next));

        // then
        verify(pushSender).send(next);
    }
}
