package gachi.chagok_server.domain.notification.push;

/**
 * 푸시 발송 창구. 운영에서는 FCM({@link FcmPushSender}), FCM 설정이 없으면 로그만 남기는 구현({@link LoggingPushSender})을 쓴다.
 */
public interface PushSender {

    /** 푸시를 보내고 결과를 돌려준다. 실패해도 예외를 던지지 않는다 (알림 목록 저장은 이미 끝난 뒤이므로). */
    PushResult send(PushMessage message);
}
