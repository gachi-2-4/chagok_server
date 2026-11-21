package gachi.chagok_server.domain.notification.push;

import java.util.Map;

/**
 * 기기로 보낼 푸시 한 건. data는 앱이 알림을 눌렀을 때 바로 화면을 열 수 있게 하는 값이다
 * (notificationId, type, documentId).
 */
public record PushMessage(String fcmToken, String title, String body, Map<String, String> data) {
}
