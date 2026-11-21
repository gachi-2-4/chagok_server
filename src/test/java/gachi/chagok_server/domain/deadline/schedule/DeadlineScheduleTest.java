package gachi.chagok_server.domain.deadline.schedule;

import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSchedule.AlertTarget;
import gachi.chagok_server.global.common.KstTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineScheduleTest {

    private static Deadline deadline(DeadlineType type, LocalDate date, Recurrence recurrence, LocalDate endDate) {
        return Deadline.fromAi(null, null, type, "field", "기한", date, recurrence, endDate, null, List.of());
    }

    private static Instant kst(int y, int m, int d, int hour, int minute) {
        return ZonedDateTime.of(y, m, d, hour, minute, 0, 0, KstTime.ZONE).toInstant();
    }

    @Test
    @DisplayName("매월 반복 기한에서 그 달에 없는 일자는 말일로 본다")
    void monthlyOccurrenceClampsToEndOfMonth() {
        // given
        LocalDate first = LocalDate.of(2026, 1, 31);

        // when
        List<LocalDate> result = DeadlineSchedule.occurrences(first, Recurrence.MONTHLY, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        // then
        assertThat(result).containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("반복 기한은 endDate 이후로 회차를 만들지 않는다")
    void monthlyStopsAtEndDate() {
        // given
        LocalDate first = LocalDate.of(2026, 11, 25);

        // when
        List<LocalDate> result = DeadlineSchedule.occurrences(first, Recurrence.MONTHLY, LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));

        // then
        assertThat(result).containsExactly(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 25));
    }

    @Test
    @DisplayName("조회 기간이 첫 회차보다 한참 뒤여도 기간 안의 회차만 준다")
    void occurrencesInLaterRange() {
        // when
        List<LocalDate> result = DeadlineSchedule.occurrences(LocalDate.of(2026, 1, 10), Recurrence.MONTHLY, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31));

        // then
        assertThat(result).containsExactly(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10));
    }

    @Test
    @DisplayName("한 번뿐인 기한이 이미 지났으면 다음 회차가 없다")
    void nextOnOrAfterPassed() {
        // when
        Optional<LocalDate> next = DeadlineSchedule.nextOnOrAfter(LocalDate.of(2026, 1, 1), null, null, LocalDate.of(2026, 2, 1));

        // then
        assertThat(next).isEmpty();
    }

    @Test
    @DisplayName("오늘이 회차 당일이면 오늘을 다음 회차로 본다")
    void nextOnOrAfterIncludesToday() {
        // when
        Optional<LocalDate> next = DeadlineSchedule.nextOnOrAfter(LocalDate.of(2026, 1, 25), Recurrence.MONTHLY, null,
                LocalDate.of(2026, 11, 25));

        // then
        assertThat(next).contains(LocalDate.of(2026, 11, 25));
    }

    @Test
    @DisplayName("모든 회차가 지난 기한은 마지막 회차 날짜를 보여 준다")
    void displayDateFallsBackToLastOccurrence() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, LocalDate.of(2026, 1, 25), Recurrence.MONTHLY, LocalDate.of(2026, 3, 25));

        // when
        LocalDate date = DeadlineSchedule.displayDate(deadline, LocalDate.of(2026, 6, 1));

        // then
        assertThat(date).isEqualTo(LocalDate.of(2026, 3, 25));
    }

    @Test
    @DisplayName("다음 알림은 회차 날짜에서 미리 알림 일수를 뺀 날의 알림 시각이다")
    void nextAlertForSingleDeadline() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, LocalDate.of(2027, 2, 28), null, null);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(30, 7, 1), true, null);

        // when
        Optional<Instant> next = DeadlineSchedule.nextAlert(deadline, reminder, kst(2026, 11, 18, 10, 0));

        // then
        assertThat(next).contains(kst(2027, 1, 29, 9, 0));
    }

    @Test
    @DisplayName("이미 지난 알림은 건너뛰고 그다음 알림을 고른다")
    void nextAlertSkipsPastAlerts() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, LocalDate.of(2027, 2, 28), null, null);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(30, 7, 1), true, null);

        // when: 30일 전 알림(1/29)을 이미 보낸 뒤
        Optional<Instant> next = DeadlineSchedule.nextAlert(deadline, reminder, kst(2027, 1, 29, 9, 0));

        // then
        assertThat(next).contains(kst(2027, 2, 21, 9, 0));
    }

    @Test
    @DisplayName("매월 반복 납부는 다음 회차의 하루 전·당일에 알린다")
    void nextAlertForMonthlyPayment() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, LocalDate.of(2026, 11, 25), Recurrence.MONTHLY, null);
        Reminder reminder = new Reminder(deadline, LocalTime.of(8, 0), List.of(1, 0), true, null);

        // when: 11월 25일 당일 알림을 보낸 직후
        Optional<Instant> next = DeadlineSchedule.nextAlert(deadline, reminder, kst(2026, 11, 25, 8, 0));

        // then: 12월 24일(하루 전)
        assertThat(next).contains(kst(2026, 12, 24, 8, 0));
    }

    @Test
    @DisplayName("매월 반복을 끈 알림은 정해 둔 회차 한 번만 보내고 끝난다")
    void nextAlertWithoutRepeat() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, LocalDate.of(2026, 11, 25), Recurrence.MONTHLY, null);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(0), false, LocalDate.of(2026, 11, 25));

        // when
        Optional<Instant> beforeDay = DeadlineSchedule.nextAlert(deadline, reminder, kst(2026, 11, 20, 0, 0));
        Optional<Instant> afterDay = DeadlineSchedule.nextAlert(deadline, reminder, kst(2026, 11, 25, 9, 0));

        // then
        assertThat(beforeDay).contains(kst(2026, 11, 25, 9, 0));
        assertThat(afterDay).isEmpty();
    }

    @Test
    @DisplayName("알림 일시로 어느 회차의 며칠 전 알림인지 거꾸로 찾는다")
    void resolveTarget() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, LocalDate.of(2027, 2, 28), null, null);
        Reminder reminder = new Reminder(deadline, LocalTime.of(9, 0), List.of(30, 7, 1), true, null);

        // when
        Optional<AlertTarget> target = DeadlineSchedule.resolveTarget(deadline, reminder, kst(2027, 2, 21, 9, 0));

        // then
        assertThat(target).contains(new AlertTarget(LocalDate.of(2027, 2, 28), 7));
    }

    @Test
    @DisplayName("기한 종류별 기본 미리 알림 일수")
    void defaultAdvanceDays() {
        assertThat(DeadlineSchedule.defaultAdvanceDays(DeadlineType.PAYMENT, true)).containsExactly(1, 0);
        assertThat(DeadlineSchedule.defaultAdvanceDays(DeadlineType.PAYMENT, false)).containsExactly(7, 1, 0);
        assertThat(DeadlineSchedule.defaultAdvanceDays(DeadlineType.EXPIRY, false)).containsExactly(30, 7, 1);
        assertThat(DeadlineSchedule.defaultAdvanceDays(DeadlineType.ACTION, false)).containsExactly(30, 7, 1);
    }
}
