package gachi.chagok_server.domain.auth.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 회원 탈퇴 뒤 소셜 서비스 쪽 연결을 끊는다 (비동기). 실패해도 이미 끝난 탈퇴에는 영향이 없다.
 * 카카오는 연결 끊기를 호출한다. 구글은 토큰 철회에 로그인 때 받은 토큰이 필요한데 저장하지 않아 호출하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUnlinkService {

    private final KakaoOAuthClient kakao;

    @Async
    public void unlinkKakao(List<String> kakaoUserIds) {
        if (!kakao.canUnlink()) {
            log.info("카카오 어드민 키(KAKAO_ADMIN_KEY)가 없어 카카오 연결 끊기를 건너뛰어요.");
            return;
        }
        for (String id : kakaoUserIds) {
            try {
                kakao.unlink(id);
            } catch (RuntimeException e) {
                log.warn("카카오 연결 끊기에 실패했어요: {}", e.toString());
            }
        }
    }
}
