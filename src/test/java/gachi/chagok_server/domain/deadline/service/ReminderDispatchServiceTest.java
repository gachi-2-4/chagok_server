package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.KstTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReminderDispatchServiceTest {

    @Mock ReminderRepository reminders;
    @Mock NotificationPublisher publisher;

    private ReminderDispatchService service;
    private Deadline deadline;
    private Reminder reminder;

    private static Instant kst(int y, int m, int d, int hour, int minute) {
        return ZonedDateTime.of(y, m, d, hour, minute, 0, 0, KstTime.ZONE).toInstant();
    }

    @BeforeEach
    void setUp() {
        service = new ReminderDispatchService(reminders, publisher);
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        Document document = new Document(user, box, "임대차계약서", false, null, 1, null);
        deadline = Deadline.fromAi(user, document, DeadlineType.EXPIRY, "contractEndDate", "계약 만료",
                LocalDate.of(2026, 11, 25), null, null, null, List.of());
        ReflectionTestUtils.setField(deadline, "id", 70L);
        reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(1, 0), true, null);
    }

    @Test
    @DisplayName("보낼 때가 된 알림은 DEADLINE_DUE 알림을 만들고 다음 알림 시각으로 옮긴다")
    void dispatchesDueReminder() {
        // given: 하루 전(11/24 09:00) 알림 시각이 지남
        reminder.scheduleNext(kst(2026, 11, 24, 9, 0));
        given(reminders.findDue(any())).willReturn(List.of(reminder));

        // when
        int sent = service.dispatchDue(kst(2026, 11, 24, 9, 5));

        // then
        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(any(User.class), eq(NotificationType.DEADLINE_DUE), title.capture(), message.capture(), any());
        assertThat(title.getValue()).isEqualTo("계약 만료까지 1일 남았어요");
        assertThat(message.getValue()).contains("11월 25일").contains("계약 만료");
        assertThat(reminder.getNextAlertAt()).isEqualTo(kst(2026, 11, 25, 9, 0));
    }

    @Test
    @DisplayName("당일 알림이 나가면 더 보낼 알림이 없어 예약이 비워진다")
    void lastAlertClearsSchedule() {
        // given
        reminder.scheduleNext(kst(2026, 11, 25, 9, 0));
        given(reminders.findDue(any())).willReturn(List.of(reminder));

        // when
        int sent = service.dispatchDue(kst(2026, 11, 25, 9, 1));

        // then
        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(any(User.class), any(), title.capture(), any(), any());
        assertThat(title.getValue()).isEqualTo("오늘은 계약 만료 날이에요");
        assertThat(reminder.getNextAlertAt()).isNull();
    }

    @Test
    @DisplayName("서버가 멈춰 있어 밀린 알림은 한 번에 이어서 보낸다")
    void catchesUpMissedAlerts() {
        // given: 하루 전·당일 알림이 모두 지난 뒤에 서버가 켜짐
        reminder.scheduleNext(kst(2026, 11, 24, 9, 0));
        given(reminders.findDue(any())).willReturn(List.of(reminder));

        // when
        int sent = service.dispatchDue(kst(2026, 11, 26, 0, 0));

        // then
        assertThat(sent).isEqualTo(2);
        assertThat(reminder.getNextAlertAt()).isNull();
    }

    @Test
    @DisplayName("아직 때가 되지 않은 알림은 보내지 않는다")
    void skipsNotYetDue() {
        // given
        reminder.scheduleNext(kst(2026, 11, 24, 9, 0));
        given(reminders.findDue(any())).willReturn(List.of(reminder));

        // when
        int sent = service.dispatchDue(kst(2026, 11, 24, 8, 0));

        // then
        assertThat(sent).isZero();
        verify(publisher, never()).publish(any(), any(), any(), any(), any());
    }
}
