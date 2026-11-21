package gachi.chagok_server.global.config;

import gachi.chagok_server.domain.notification.push.FcmPushSender;
import gachi.chagok_server.domain.notification.push.LoggingPushSender;
import gachi.chagok_server.domain.notification.push.PushSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PushConfigTest {

    private final PushConfig config = new PushConfig();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static String privateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    @DisplayName("FCM 설정이 하나라도 비어 있으면 로그만 남기는 발송기를 쓴다")
    void loggingWhenNotConfigured() throws Exception {
        String key = privateKeyPem();

        assertThat(config.pushSender("", "", "", mapper)).isInstanceOf(LoggingPushSender.class);
        assertThat(config.pushSender("chagok", "", key, mapper)).isInstanceOf(LoggingPushSender.class);
        assertThat(config.pushSender("chagok", "fcm@x.iam.gserviceaccount.com", "  ", mapper)).isInstanceOf(LoggingPushSender.class);
    }

    @Test
    @DisplayName("프로젝트 ID·서비스 계정 이메일·개인 키가 모두 있으면 FCM으로 보낸다")
    void fcmWhenConfigured() throws Exception {
        PushSender sender = config.pushSender("chagok", "fcm@x.iam.gserviceaccount.com", privateKeyPem(), mapper);

        assertThat(sender).isInstanceOf(FcmPushSender.class);
    }
}
