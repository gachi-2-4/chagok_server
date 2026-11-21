package gachi.chagok_server.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FCM HTTP v1 API(`projects/{projectId}/messages:send`)로 푸시를 보낸다.
 * 토큰이 만료됐다고(UNREGISTERED) 알려 주면 {@link PushResult#TOKEN_INVALID}를 돌려줘 서버가 그 토큰을 지우게 한다.
 */
@Slf4j
public class FcmPushSender implements PushSender {

    private final RestClient client;
    private final FcmAccessTokenProvider tokens;
    private final String sendPath;
    private final JsonMapper jsonMapper;

    public FcmPushSender(RestClient client, FcmAccessTokenProvider tokens, String projectId, JsonMapper jsonMapper) {
        this.client = client;
        this.tokens = tokens;
        this.sendPath = "/v1/projects/" + projectId + "/messages:send";
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PushResult send(PushMessage message) {
        try {
            PushResult result = post(message);
            if (result == null) {
                // 401: 액세스 토큰이 만료됐을 수 있어 새로 받아 한 번만 다시 시도한다.
                tokens.invalidate();
                result = post(message);
            }
            return result == null ? PushResult.FAILED : result;
        } catch (RuntimeException e) {
            log.warn("FCM 발송 중 오류: {}", e.toString());
            return PushResult.FAILED;
        }
    }

    /** @return 결과. 401이면 null (다시 시도할 수 있음) */
    private PushResult post(PushMessage message) {
        record Outcome(HttpStatusCode status, JsonNode body) {
        }
        Outcome outcome = client.post().uri(sendPath)
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(message))
                .exchange((request, response) -> {
                    JsonNode body = null;
                    try {
                        body = jsonMapper.readTree(response.getBody());
                    } catch (RuntimeException | java.io.IOException ignored) {
                        // 본문이 없거나 JSON이 아니어도 상태 코드로 판단한다.
                    }
                    return new Outcome(response.getStatusCode(), body);
                });

        int status = outcome.status().value();
        if (outcome.status().is2xxSuccessful()) {
            return PushResult.SENT;
        }
        if (status == 401) {
            return null;
        }
        if (isUnregistered(status, outcome.body())) {
            return PushResult.TOKEN_INVALID;
        }
        log.warn("FCM 발송 실패: status={}, error={}", status, errorStatus(outcome.body()));
        return PushResult.FAILED;
    }

    /** FCM v1 요청 Body. data 값은 모두 문자열이어야 한다. */
    static Map<String, Object> body(PushMessage message) {
        Map<String, Object> fcm = new LinkedHashMap<>();
        fcm.put("token", message.fcmToken());
        fcm.put("notification", Map.of("title", message.title(), "body", message.body()));
        fcm.put("data", message.data());
        fcm.put("android", Map.of("priority", "HIGH"));
        fcm.put("apns", Map.of("payload", Map.of("aps", Map.of("sound", "default"))));
        return Map.of("message", fcm);
    }

    /** 토큰이 만료·해지됐을 때: 404(UNREGISTERED). */
    static boolean isUnregistered(int status, JsonNode body) {
        if (status == 404) {
            return true;
        }
        if (body == null || !body.has("error")) {
            return false;
        }
        JsonNode details = body.get("error").get("details");
        if (details != null && details.isArray()) {
            for (JsonNode detail : details) {
                if (detail.hasNonNull("errorCode") && "UNREGISTERED".equals(detail.get("errorCode").asString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String errorStatus(JsonNode body) {
        return body != null && body.has("error") && body.get("error").hasNonNull("status")
                ? body.get("error").get("status").asString() : null;
    }
}
