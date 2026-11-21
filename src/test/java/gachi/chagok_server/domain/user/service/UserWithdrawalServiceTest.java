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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 1L;

    @Mock UserRepository users;
    @Mock DocumentDeletionService documentDeletion;
    @Mock ComparisonRepository comparisons;
    @Mock ChatRepository chats;
    @Mock BoxRepository boxes;
    @Mock NotificationRepository notifications;
    @Mock NotificationSettingsRepository notificationSettings;
    @Mock PushTokenRepository pushTokens;
    @Mock UserProfileRepository profiles;
    @Mock SocialAccountRepository socialAccounts;
    @Mock TokenService tokens;
    @InjectMocks UserWithdrawalService service;

    @Test
    @DisplayName("탈퇴하면 세션을 끊고, 데이터를 순서대로 지우고, 계정 개인정보를 비운다")
    void withdraw() {
        // given
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        user.setBirthDateOnce(java.time.LocalDate.of(1990, 1, 1));
        given(users.findById(USER_ID)).willReturn(Optional.of(user));

        // when
        service.withdraw(USER_ID);

        // then 순서: 세션 → 대화·알림 → 서류·비교·박스 → 프로필·소셜
        InOrder order = inOrder(tokens, chats, notifications, pushTokens, notificationSettings, documentDeletion, comparisons,
                boxes, profiles, socialAccounts);
        order.verify(tokens).revokeAll(USER_ID);
        order.verify(chats).deleteMessagesByUserId(USER_ID);
        order.verify(chats).deleteDocumentLinksByUserId(USER_ID);
        order.verify(chats).deleteBoxLinksByUserId(USER_ID);
        order.verify(chats).deleteAllByUserId(USER_ID);
        order.verify(notifications).deleteAllByUserId(USER_ID);
        order.verify(pushTokens).deleteAllByUserId(USER_ID);
        order.verify(notificationSettings).deleteById(USER_ID);
        order.verify(documentDeletion).deleteAllOfUser(USER_ID);
        order.verify(comparisons).deleteAllByUserId(USER_ID);
        order.verify(boxes).deleteAllByUserId(USER_ID);
        order.verify(profiles).deleteById(USER_ID);
        order.verify(socialAccounts).deleteAllByUserId(USER_ID);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getBirthDate()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
    }

    @Test
    @DisplayName("이미 탈퇴했거나 없는 사용자는 401이고 아무것도 지우지 않는다")
    void alreadyWithdrawn() {
        // given
        User gone = User.phoneUser("01012345678", "hash", "홍길동");
        gone.withdraw();
        given(users.findById(USER_ID)).willReturn(Optional.of(gone));
        given(users.findById(2L)).willReturn(Optional.empty());

        // when & then
        for (Long id : new Long[]{USER_ID, 2L}) {
            assertThatThrownBy(() -> service.withdraw(id)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }
        verifyNoInteractions(tokens, chats, documentDeletion, boxes);
    }
}
