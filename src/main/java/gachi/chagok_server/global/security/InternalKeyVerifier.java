package gachi.chagok_server.global.security;

import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * AI 서버가 부르는 내부 API의 X-Internal-Key 검증. 사용자 토큰은 쓰지 않는다.
 */
@Component
public class InternalKeyVerifier {

    public static final String HEADER = "X-Internal-Key";

    private final byte[] internalKey;

    public InternalKeyVerifier(@Value("${chagok.internal.key}") String key) {
        this.internalKey = key.getBytes(StandardCharsets.UTF_8);
    }

    /** 키가 없거나 다르면 403. 시간차 공격을 막기 위해 일정 시간 비교를 쓴다. */
    public void verify(String key) {
        if (key == null || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
