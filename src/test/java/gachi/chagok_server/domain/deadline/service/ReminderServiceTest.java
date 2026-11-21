package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.ReminderCreateRequest;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.ReminderResponse;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.ReminderUpdateRequest;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    private static final Long USER_ID = 1L;

    @Mock ReminderRepository reminders;
    @Mock DeadlineRepository deadlines;
    @Mock NotificationSettingsRepository settings;

    private ReminderService service;
    private User user;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new ReminderService(reminders, deadlines, settings, new DeadlineAssembler());
        user = User.phoneUser("01012345678", "hash", "홍길동");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        document = new Document(user, box, "전세 임대차 계약서", false, null, 1, null);
    }

    private Deadline deadline(DeadlineType type, LocalDate date, Recurrence recurrence) {
        Deadline deadline = Deadline.fromAi(user, document, type, "contractEndDate", "계약 만료", date, recurrence, null, null, List.of());
        ReflectionTestUtils.setField(deadline, "id", 70L);
        return deadline;
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("알림 등록은 기한 종류별 기본값과 기본 알림 시각(09:00)으로 켠다")
    void createWithDefaults() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().plusDays(100), null);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.of(deadline));
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());
        given(settings.findById(USER_ID)).willReturn(Optional.empty());
        given(reminders.saveAndFlush(any(Reminder.class))).willAnswer(i -> i.getArgument(0));

        // when
        ReminderResponse response = service.create(USER_ID, new ReminderCreateRequest(70L, null, null, null));

        // then
        assertThat(response.advanceDays()).containsExactly(30, 7, 1);
        assertThat(response.alertTime()).isEqualTo("09:00");
        assertThat(response.repeatMonthly()).isFalse();
        assertThat(response.nextAlertAt()).isNotNull();
    }

    @Test
    @DisplayName("알림 시각과 미리 알림은 보낸 값으로 켜고, 미리 알림은 큰 값부터 중복 없이 정리한다")
    void createWithCustomValues() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, KstTime.today().plusDays(40), Recurrence.MONTHLY);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.of(deadline));
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());
        given(reminders.saveAndFlush(any(Reminder.class))).willAnswer(i -> i.getArgument(0));

        // when
        ReminderResponse response = service.create(USER_ID, new ReminderCreateRequest(70L, "08:30", List.of(0, 3, 3, 1), true));

        // then
        assertThat(response.alertTime()).isEqualTo("08:30");
        assertThat(response.advanceDays()).containsExactly(3, 1, 0);
        assertThat(response.repeatMonthly()).isTrue();
    }

    @Test
    @DisplayName("없는 기한이면 DEADLINE_NOT_FOUND")
    void createDeadlineNotFound() {
        // given
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.empty());

        // when & then
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, null, null)), ErrorCode.DEADLINE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 알림이 있으면 REMINDER_ALREADY_EXISTS")
    void createAlreadyExists() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().plusDays(100), null);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.of(deadline));
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.of(new Reminder(deadline, LocalTime.of(9, 0), List.of(1), true, null)));

        // when & then
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, null, null)), ErrorCode.REMINDER_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("이미 지난 기한이면 DEADLINE_PASSED")
    void createPassedDeadline() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().minusDays(1), null);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.of(deadline));
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());

        // when & then
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, null, null)), ErrorCode.DEADLINE_PASSED);
    }

    @Test
    @DisplayName("미리 알림은 0–90 사이 정수 1–5개만 받는다")
    void invalidAdvanceDays() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().plusDays(100), null);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.of(deadline));
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());

        // when & then
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, List.of(), null)), ErrorCode.INVALID_ADVANCE_DAYS);
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, List.of(91), null)), ErrorCode.INVALID_ADVANCE_DAYS);
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, List.of(-1), null)), ErrorCode.INVALID_ADVANCE_DAYS);
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, null, List.of(0, 1, 2, 3, 4, 5), null)), ErrorCode.INVALID_ADVANCE_DAYS);
    }

    @Test
    @DisplayName("알림 시각은 10분 단위만 받는다")
    void invalidAlertTime() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().plusDays(100), null);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(70L, USER_ID)).willReturn(Optional.of(deadline));
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());

        // when & then
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, "09:05", null, null)), ErrorCode.INVALID_ALERT_TIME);
        assertError(() -> service.create(USER_ID, new ReminderCreateRequest(70L, "아침", null, null)), ErrorCode.INVALID_ALERT_TIME);
    }

    @Test
    @DisplayName("알림 수정은 보낸 값만 바꾸고 예약 시각을 다시 계산한다")
    void updatePartially() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, KstTime.today().plusDays(40), Recurrence.MONTHLY);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(1, 0), true, null);
        given(reminders.findOwned(33L, USER_ID)).willReturn(Optional.of(reminder));

        // when
        ReminderResponse response = service.update(USER_ID, 33L, new ReminderUpdateRequest("08:00", List.of(1, 3), null));

        // then
        assertThat(response.alertTime()).isEqualTo("08:00");
        assertThat(response.advanceDays()).containsExactly(3, 1);
        assertThat(response.repeatMonthly()).isTrue();
        assertThat(reminder.getNextAlertAt()).isNotNull();
    }

    @Test
    @DisplayName("반복을 끄면 가장 가까운 회차 한 번만 보낸다")
    void updateTurnsOffRepeat() {
        // given
        LocalDate first = KstTime.today().plusDays(10);
        Deadline deadline = deadline(DeadlineType.PAYMENT, first, Recurrence.MONTHLY);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(0), true, null);
        given(reminders.findOwned(33L, USER_ID)).willReturn(Optional.of(reminder));

        // when
        service.update(USER_ID, 33L, new ReminderUpdateRequest(null, null, false));

        // then
        assertThat(reminder.isRepeatMonthly()).isFalse();
        assertThat(reminder.getOnceDate()).isEqualTo(first);
    }

    @Test
    @DisplayName("없는 알림이면 REMINDER_NOT_FOUND")
    void updateNotFound() {
        // given
        given(reminders.findOwned(33L, USER_ID)).willReturn(Optional.empty());

        // when & then
        assertError(() -> service.update(USER_ID, 33L, new ReminderUpdateRequest(null, null, null)), ErrorCode.REMINDER_NOT_FOUND);
        assertError(() -> service.delete(USER_ID, 33L), ErrorCode.REMINDER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림을 끄면 알림을 지우고 기한에는 끈 표시를 남긴다")
    void deleteMarksDisabled() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().plusDays(100), null);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(1), true, null);
        given(reminders.findOwned(33L, USER_ID)).willReturn(Optional.of(reminder));

        // when
        service.delete(USER_ID, 33L);

        // then
        verify(reminders).delete(reminder);
        assertThat(deadline.isReminderDisabled()).isTrue();
    }

    @Test
    @DisplayName("사용자가 끈 기한은 자동으로 알림을 다시 만들지 않는다")
    void autoCreateSkipsDisabled() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().plusDays(100), null);
        deadline.disableReminder();

        // when
        Optional<Reminder> created = service.autoCreate(deadline);

        // then
        assertThat(created).isEmpty();
        verifyNoInteractions(reminders);
    }

    @Test
    @DisplayName("기한이 생기면 기본값으로 알림을 자동 예약한다")
    void autoCreate() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, KstTime.today().plusDays(40), Recurrence.MONTHLY);
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());
        given(settings.findById(USER_ID)).willReturn(Optional.empty());
        given(reminders.save(any(Reminder.class))).willAnswer(i -> i.getArgument(0));

        // when
        Optional<Reminder> created = service.autoCreate(deadline);

        // then
        assertThat(created).hasValueSatisfying(reminder -> {
            assertThat(reminder.advanceDayList()).containsExactly(1, 0);
            assertThat(reminder.getAlertTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(reminder.getNextAlertAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("보낼 알림이 하나도 남지 않은 지난 기한은 자동 예약하지 않는다")
    void autoCreateSkipsPassedDeadline() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, KstTime.today().minusDays(10), null);
        given(reminders.findByDeadlineId(70L)).willReturn(Optional.empty());
        given(settings.findById(USER_ID)).willReturn(Optional.empty());

        // when
        Optional<Reminder> created = service.autoCreate(deadline);

        // then
        assertThat(created).isEmpty();
    }
}
