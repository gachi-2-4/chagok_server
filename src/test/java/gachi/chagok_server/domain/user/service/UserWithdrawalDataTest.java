package gachi.chagok_server.domain.user.service;

import gachi.chagok_server.domain.auth.service.TokenService;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.chat.entity.Chat;
import gachi.chagok_server.domain.chat.entity.ChatBoxLink;
import gachi.chagok_server.domain.chat.entity.ChatDocumentLink;
import gachi.chagok_server.domain.chat.entity.ChatMessage;
import gachi.chagok_server.domain.chat.repository.ChatBoxLinkRepository;
import gachi.chagok_server.domain.chat.repository.ChatDocumentLinkRepository;
import gachi.chagok_server.domain.chat.repository.ChatMessageRepository;
import gachi.chagok_server.domain.chat.repository.ChatRepository;
import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.Comparison;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.service.DeadlineAssembler;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentCheckItem;
import gachi.chagok_server.domain.document.entity.DocumentPage;
import gachi.chagok_server.domain.document.entity.FailReason;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentCheckItemRepository;
import gachi.chagok_server.domain.document.repository.DocumentPageRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.service.DocumentDeletionService;
import gachi.chagok_server.domain.document.service.StorageCleaner;
import gachi.chagok_server.domain.notification.entity.Notification;
import gachi.chagok_server.domain.notification.entity.NotificationSettings;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.entity.PushToken;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.user.entity.ConsentHistory;
import gachi.chagok_server.domain.user.entity.ConsentType;
import gachi.chagok_server.domain.user.entity.SocialAccount;
import gachi.chagok_server.domain.user.entity.SocialProvider;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.entity.UserProfile;
import gachi.chagok_server.domain.user.repository.ConsentHistoryRepository;
import gachi.chagok_server.domain.user.repository.SocialAccountRepository;
import gachi.chagok_server.domain.user.repository.UserProfileRepository;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.config.AppConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 탈퇴가 실제 JPA 매핑(외래 키 순서 포함)에서 그 사용자의 데이터만 모두 지우는지 확인한다.
 * 저장소 파일 삭제는 커밋 뒤에 도는데 이 테스트의 트랜잭션은 롤백되므로 여기서는 확인하지 않는다.
 */
@DataJpaTest
@Import({AppConfig.class, UserWithdrawalService.class, DocumentDeletionService.class, ReminderService.class, DeadlineAssembler.class})
@TestPropertySource(properties = {"spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
class UserWithdrawalDataTest {

    @MockitoBean TokenService tokenService;
    @MockitoBean StorageCleaner storageCleaner;
    @MockitoBean gachi.chagok_server.domain.auth.oauth.SocialUnlinkService socialUnlink;

    @Autowired UserWithdrawalService withdrawal;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired BoxRepository boxes;
    @Autowired DocumentRepository documents;
    @Autowired DocumentAnalysisResultRepository results;
    @Autowired DocumentPageRepository pages;
    @Autowired DocumentCheckItemRepository checkItems;
    @Autowired DeadlineRepository deadlines;
    @Autowired ReminderRepository reminders;
    @Autowired ComparisonRepository comparisons;
    @Autowired ChatRepository chats;
    @Autowired ChatMessageRepository chatMessages;
    @Autowired ChatDocumentLinkRepository chatDocuments;
    @Autowired ChatBoxLinkRepository chatBoxes;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationSettingsRepository notificationSettings;
    @Autowired PushTokenRepository pushTokens;
    @Autowired UserProfileRepository profiles;
    @Autowired SocialAccountRepository socialAccounts;
    @Autowired ConsentHistoryRepository consents;

    private User leaving;
    private User staying;

    @BeforeEach
    void setUp() {
        leaving = users.save(User.phoneUser("01012345678", "hash", "탈퇴할 사람"));
        staying = users.save(User.phoneUser("01099998888", "hash", "남는 사람"));
        populate(leaving, "A");
        populate(staying, "B");
        // 실제 요청처럼 탈퇴 트랜잭션이 빈 영속성 컨텍스트에서 시작하게 한다. (대량 삭제는 컨텍스트에 남은 엔티티를 모른다.)
        em.clear();
    }

    /** 사용자 한 명이 쓸 수 있는 모든 종류의 데이터를 만든다. */
    private void populate(User user, String tag) {
        Box box = boxes.save(new Box(user, "박스" + tag, BoxColor.PEACH, BoxIcon.HOME));
        Document contract = analyzed(user, box, "계약서" + tag, DocCategory.CONTRACT);
        Document bill = analyzed(user, box, "고지서" + tag, DocCategory.BILL);
        Document failed = documents.save(new Document(user, box, "실패" + tag, false, null, 1, null));
        failed.fail(FailReason.ANALYSIS_ERROR);

        results.save(new DocumentAnalysisResult(contract));
        pages.save(new DocumentPage(contract, 1, "documents/k" + tag, 100, 100));
        checkItems.save(new DocumentCheckItem(contract, "fixedDate", "확정일자", false));
        Deadline deadline = deadlines.save(Deadline.fromAi(user, contract, DeadlineType.EXPIRY, "end", "계약 만료",
                LocalDate.of(2030, 1, 1), null, null, null, List.of()));
        reminders.save(new Reminder(deadline, LocalTime.of(9, 0), List.of(1), true, null));
        comparisons.save(new Comparison(user, contract.getId(), bill.getId(), CompareType.BILL));

        Chat chat = chats.save(new Chat(user));
        chat.nameFromFirstQuestion("질문" + tag);
        chatMessages.save(ChatMessage.question(chat, "질문" + tag));
        chatDocuments.save(new ChatDocumentLink(chat, contract.getId()));
        chatBoxes.save(new ChatBoxLink(chat, box.getId()));

        notifications.save(new Notification(user, NotificationType.ANALYSIS_COMPLETED, "분석 완료" + tag, "내용"));
        notificationSettings.save(new NotificationSettings(user));
        PushToken token = new PushToken("00000000-0000-0000-0000-00000000000" + (tag.equals("A") ? "1" : "2"), user);
        token.update(user, "fcm" + tag, "IOS", true, "1.0");
        pushTokens.save(token);
        UserProfile profile = new UserProfile(user);
        profile.update("KR", "서울", "마포구", "MONTHLY_RENT", "NONE");
        profiles.save(profile);
        socialAccounts.save(new SocialAccount(user, SocialProvider.KAKAO, "kakao" + tag));
        consents.save(new ConsentHistory(user, ConsentType.TERMS_OF_SERVICE, true, "2026-11-01"));
        em.flush();
    }

    private Document analyzed(User user, Box box, String title, DocCategory category) {
        Document document = documents.save(new Document(user, box, "임시", false, null, 1, null));
        document.completeAnalysis(title, DocType.ETC, category, "요약", null, null, null, null, false);
        return document;
    }

    @Test
    @DisplayName("탈퇴하면 그 사용자의 서류·박스·비교·대화·알림·토큰·프로필·소셜 연결이 모두 지워진다")
    void deletesEverythingOfTheUser() {
        // when
        withdrawal.withdraw(leaving.getId());
        em.flush();
        em.clear();

        // then
        Long id = leaving.getId();
        assertThat(documents.findByUserId(id)).isEmpty();
        assertThat(boxes.countByUserId(id)).isZero();
        assertThat(chats.findListed(id, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(notifications.countByUserIdAndReadAtIsNull(id)).isZero();
        assertThat(notifications.findByUserId(id, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(notificationSettings.findById(id)).isEmpty();
        assertThat(pushTokens.findByUserIdAndPermissionGrantedTrue(id)).isEmpty();
        assertThat(profiles.findById(id)).isEmpty();
        assertThat(socialAccounts.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakaoA")).isEmpty();
        assertThat(deadlines.findActiveByUserId(id)).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 데이터는 그대로 남는다")
    void keepsOtherUsersData() {
        // when
        withdrawal.withdraw(leaving.getId());
        em.flush();
        em.clear();

        // then: B가 가진 것은 그대로
        Long id = staying.getId();
        assertThat(documents.findByUserId(id)).hasSize(3);
        assertThat(boxes.countByUserId(id)).isEqualTo(1);
        assertThat(chats.findListed(id, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(1);
        assertThat(chatMessages.count()).isEqualTo(1);
        assertThat(chatDocuments.count()).isEqualTo(1);
        assertThat(chatBoxes.count()).isEqualTo(1);
        assertThat(comparisons.count()).isEqualTo(1);
        assertThat(deadlines.count()).isEqualTo(1);
        assertThat(reminders.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
        assertThat(pages.count()).isEqualTo(1);
        assertThat(checkItems.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(pushTokens.count()).isEqualTo(1);
        assertThat(profiles.findById(id)).isPresent();
        assertThat(socialAccounts.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakaoB")).isPresent();
    }

    @Test
    @DisplayName("사용자 행은 개인정보만 비우고 남기며, 약관 동의 이력은 보관한다")
    void anonymizesUserAndKeepsConsentHistory() {
        // when
        withdrawal.withdraw(leaving.getId());
        em.flush();
        em.clear();

        // then
        User user = users.findById(leaving.getId()).orElseThrow();
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(consents.findByUserIdOrderByUpdatedAtDescIdDesc(leaving.getId())).hasSize(1);
        // 같은 전화번호로 다시 가입할 수 있다.
        assertThat(users.existsByPhoneNumber("01012345678")).isFalse();
    }
}
