package gachi.chagok_server.domain.user.service;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.chat.repository.ChatRepository;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.document.service.DocumentDeletionService;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.SocialAccountRepository;
import gachi.chagok_server.domain.user.repository.UserProfileRepository;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 계정과 모든 데이터를 한 트랜잭션으로 지우고, 저장소 파일은 커밋된 뒤 비동기로 지운다.
 * 복구할 수 없다. 약관 동의 이력은 법령상 보관이 필요해 지우지 않는다 (사용자 행은 개인정보만 비우고 남김).
 * <p>
 * 아직 하지 않는 것: AI 서버의 벡터 인덱스 삭제 요청(명세에 API가 없음), 카카오 연결 끊기·구글 토큰 철회
 * (로그인 때 받은 토큰을 저장하지 않고 카카오 어드민 키 설정도 없음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserWithdrawalService {

    private final UserRepository users;
    private final DocumentDeletionService documentDeletion;
    private final ComparisonRepository comparisons;
    private final ChatRepository chats;
    private final BoxRepository boxes;
    private final NotificationRepository notifications;
    private final NotificationSettingsRepository notificationSettings;
    private final PushTokenRepository pushTokens;
    private final UserProfileRepository profiles;
    private final SocialAccountRepository socialAccounts;
    private final TokenService tokens;

    @Transactional
    public void withdraw(Long userId) {
        User user = users.findById(userId).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 로그인 세션부터 끊는다. 이후 발급된 액세스 토큰도 탈퇴한 사용자는 인증 필터가 거부한다.
        tokens.revokeAll(userId);

        // 대화·알림·푸시 토큰
        chats.deleteMessagesByUserId(userId);
        chats.deleteDocumentLinksByUserId(userId);
        chats.deleteBoxLinksByUserId(userId);
        chats.deleteAllByUserId(userId);
        notifications.deleteAllByUserId(userId);
        pushTokens.deleteAllByUserId(userId);
        notificationSettings.deleteById(userId);

        // 서류와 그에 딸린 파일·분석 결과·기한·알림 예약, 비교 결과. 박스는 서류를 지운 뒤에 지운다.
        documentDeletion.deleteAllOfUser(userId);
        comparisons.deleteAllByUserId(userId);
        boxes.deleteAllByUserId(userId);

        // 프로필·소셜 연결, 그리고 계정 개인정보
        profiles.deleteById(userId);
        socialAccounts.deleteAllByUserId(userId);
        user.withdraw();
    }
}
