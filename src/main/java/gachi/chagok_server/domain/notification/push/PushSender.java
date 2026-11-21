package gachi.chagok_server.domain.notification.push;

/**
 * 푸시 발송 창구. 실제 FCM 연동(firebase-admin)은 의존성을 추가하면서 이 인터페이스의 구현체로 붙인다.
 */
public interface PushSender {

    /** 푸시를 보낸다. 실패해도 예외를 던지지 않는다(알림 목록 저장은 이미 끝난 뒤이므로). */
    void send(PushMessage message);
}
