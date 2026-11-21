package gachi.chagok_server.global.config;

import gachi.chagok_server.domain.notification.push.FcmAccessTokenProvider;
import gachi.chagok_server.domain.notification.push.FcmPushSender;
import gachi.chagok_server.domain.notification.push.LoggingPushSender;
import gachi.chagok_server.domain.notification.push.PushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * 푸시 발송기를 고른다. FCM 서비스 계정(프로젝트 ID·이메일·개인 키)이 모두 설정돼 있으면 FCM으로 보내고,
 * 하나라도 비어 있으면 로그만 남긴다 (로컬 개발에서 FCM 없이도 서버가 뜨도록).
 */
@Slf4j
@Configuration
public class PushConfig {

    static final String FCM_BASE_URL = "https://fcm.googleapis.com";
    static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    PushSender pushSender(@Value("${chagok.fcm.project-id:}") String projectId,
                          @Value("${chagok.fcm.client-email:}") String clientEmail,
                          @Value("${chagok.fcm.private-key:}") String privateKey,
                          JsonMapper jsonMapper) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(clientEmail) || !StringUtils.hasText(privateKey)) {
            log.warn("FCM 설정(FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY)이 없어 푸시를 로그로만 남겨요.");
            return new LoggingPushSender();
        }
        RestClient client = RestClient.builder().baseUrl(FCM_BASE_URL).requestFactory(factory()).build();
        FcmAccessTokenProvider tokens = new FcmAccessTokenProvider(clientEmail, privateKey, TOKEN_URI,
                RestClient.builder().requestFactory(factory()).build(), Clock.systemUTC());
        return new FcmPushSender(client, tokens, projectId, jsonMapper);
    }

    private static JdkClientHttpRequestFactory factory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }
}
