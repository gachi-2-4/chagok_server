package gachi.chagok_server.domain.notification.push;

import io.jsonwebtoken.Jwts;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * FCM HTTP v1 호출에 쓰는 OAuth2 액세스 토큰을 서비스 계정으로 발급받아 만료 전까지 재사용한다.
 * 서비스 계정 키(JSON)의 client_email·private_key로 서명한 JWT를 구글 토큰 서버에 주고 토큰을 받는다.
 * firebase-admin 없이 이미 있는 JWT 라이브러리(jjwt)와 RestClient로 처리한다.
 */
public class FcmAccessTokenProvider {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    /** 만료 직전에 쓰다 실패하지 않도록 이만큼 일찍 새로 받는다. */
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);
    private static final Duration ASSERTION_VALIDITY = Duration.ofHours(1);

    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String tokenUri;
    private final RestClient client;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    public FcmAccessTokenProvider(String clientEmail, String privateKeyPem, String tokenUri, RestClient client, Clock clock) {
        this.clientEmail = clientEmail;
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.tokenUri = tokenUri;
        this.client = client;
        this.clock = clock;
    }

    /** 유효한 액세스 토큰. 만료가 가까우면 새로 받는다. */
    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken == null || !now.isBefore(cachedUntil.minus(REFRESH_MARGIN))) {
            refresh(now);
        }
        return cachedToken;
    }

    /** FCM이 401을 주면 캐시를 버리고 다음에 새로 받게 한다. */
    public synchronized void invalidate() {
        cachedToken = null;
        cachedUntil = Instant.EPOCH;
    }

    private void refresh(Instant now) {
        String assertion = Jwts.builder()
                .issuer(clientEmail)
                .subject(clientEmail)
                .audience().add(tokenUri).and()
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ASSERTION_VALIDITY)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("assertion", assertion);
        JsonNode response = client.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);

        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("FCM 액세스 토큰을 받지 못했어요.");
        }
        long expiresIn = response.hasNonNull("expires_in") ? response.get("expires_in").asLong() : 3600;
        cachedToken = response.get("access_token").asString();
        cachedUntil = now.plusSeconds(expiresIn);
    }

    /** 환경변수에 넣은 PEM(줄바꿈이 문자 그대로 \n 일 수 있음)을 개인 키로 읽는다. */
    static PrivateKey parsePrivateKey(String pem) {
        try {
            String body = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new IllegalStateException("FCM 서비스 계정 개인 키를 읽지 못했어요. FCM_PRIVATE_KEY를 확인해 주세요.", e);
        }
    }
}
