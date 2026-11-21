package gachi.chagok_server.domain.deadline.schedule;

import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.global.common.KstTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 기한의 회차 날짜와 알림 시각을 계산하는 순수 함수 모음.
 * 매월 반복 기한은 첫 회차의 일자를 매월 반복하며, 그 달에 그 일자가 없으면(예: 31일) 말일로 본다.
 */
public final class DeadlineSchedule {

    /** 알림은 최대 90일 전까지 설정할 수 있다. */
    private static final int MAX_ADVANCE_DAYS = 90;

    private DeadlineSchedule() {
    }

    /** 기간 [from, to] 안의 회차 날짜를 날짜순으로 돌려준다. */
    public static List<LocalDate> occurrences(Deadline deadline, LocalDate from, LocalDate to) {
        return occurrences(deadline.getDate(), deadline.getRecurrence(), deadline.getEndDate(), from, to);
    }

    public static List<LocalDate> occurrences(LocalDate first, Recurrence recurrence, LocalDate endDate,
                                              LocalDate from, LocalDate to) {
        List<LocalDate> result = new ArrayList<>();
        if (recurrence == null) {
            if (!first.isBefore(from) && !first.isAfter(to)) {
                result.add(first);
            }
            return result;
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(from));
        for (long k = Math.max(0, months); ; k++) {
            LocalDate occurrence = monthlyOccurrence(first, k);
            if (occurrence.isAfter(to) || (endDate != null && occurrence.isAfter(endDate))) {
                break;
            }
            if (!occurrence.isBefore(from)) {
                result.add(occurrence);
            }
        }
        return result;
    }

    /** base 이후(base 포함) 첫 회차. 더 이상 회차가 없으면 비어 있다. */
    public static Optional<LocalDate> nextOnOrAfter(Deadline deadline, LocalDate base) {
        return nextOnOrAfter(deadline.getDate(), deadline.getRecurrence(), deadline.getEndDate(), base);
    }

    public static Optional<LocalDate> nextOnOrAfter(LocalDate first, Recurrence recurrence, LocalDate endDate, LocalDate base) {
        if (recurrence == null) {
            return first.isBefore(base) ? Optional.empty() : Optional.of(first);
        }
        // 매월 반복이므로 base가 든 달이나 그다음 달 안에 반드시 다음 회차가 있다.
        List<LocalDate> found = occurrences(first, recurrence, endDate, base, base.plusMonths(2));
        return found.stream().findFirst();
    }

    /**
     * 화면에 보여 줄 날짜. 오늘 이후 첫 회차가 있으면 그 날짜, 없으면(모두 지남) 마지막 회차다.
     */
    public static LocalDate displayDate(Deadline deadline, LocalDate today) {
        Optional<LocalDate> next = nextOnOrAfter(deadline, today);
        if (next.isPresent()) {
            return next.get();
        }
        if (deadline.getRecurrence() == null) {
            return deadline.getDate();
        }
        LocalDate end = deadline.getEndDate() != null ? deadline.getEndDate() : today;
        List<LocalDate> past = occurrences(deadline.getDate(), deadline.getRecurrence(), end, deadline.getDate(), end);
        return past.isEmpty() ? deadline.getDate() : past.get(past.size() - 1);
    }

    /**
     * after 이후의 다음 알림 일시. 더 보낼 알림이 없으면 비어 있다.
     * 알림 일시 = (회차 날짜 - 미리 알림 일수)의 알림 시각(KST).
     */
    public static Optional<Instant> nextAlert(Deadline deadline, Reminder reminder, Instant after) {
        LocalDate afterDate = after.atZone(KstTime.ZONE).toLocalDate();
        List<LocalDate> dates;
        if (!deadline.isRecurring()) {
            dates = List.of(deadline.getDate());
        } else if (!reminder.isRepeatMonthly()) {
            dates = reminder.getOnceDate() != null
                    ? List.of(reminder.getOnceDate())
                    : nextOnOrAfter(deadline, afterDate).map(List::of).orElse(List.of());
        } else {
            dates = occurrences(deadline, afterDate, afterDate.plusDays(MAX_ADVANCE_DAYS + 31L));
        }

        Instant best = null;
        for (LocalDate date : dates) {
            for (int advance : reminder.advanceDayList()) {
                LocalDateTime alertAt = date.minusDays(advance).atTime(reminder.getAlertTime());
                Instant instant = KstTime.toInstant(alertAt);
                if (instant.isAfter(after) && (best == null || instant.isBefore(best))) {
                    best = instant;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** 알림이 가리키는 회차 날짜와 미리 알림 일수 */
    public record AlertTarget(LocalDate date, int advanceDays) {
    }

    /** 알림 일시(alertAt)가 어느 회차의 며칠 전 알림인지 거꾸로 찾는다. */
    public static Optional<AlertTarget> resolveTarget(Deadline deadline, Reminder reminder, Instant alertAt) {
        LocalDate alertDate = alertAt.atZone(KstTime.ZONE).toLocalDate();
        for (int advance : reminder.advanceDayList()) {
            LocalDate occurrence = alertDate.plusDays(advance);
            boolean valid = deadline.isRecurring()
                    ? !occurrences(deadline, occurrence, occurrence).isEmpty()
                    : deadline.getDate().equals(occurrence);
            if (valid) {
                return Optional.of(new AlertTarget(occurrence, advance));
            }
        }
        return Optional.empty();
    }

    /** 기한 종류별 기본 미리 알림 일수. */
    public static List<Integer> defaultAdvanceDays(DeadlineType type, boolean recurring) {
        if (type == DeadlineType.PAYMENT) {
            return recurring ? List.of(1, 0) : List.of(7, 1, 0);
        }
        return List.of(30, 7, 1);
    }

    private static LocalDate monthlyOccurrence(LocalDate first, long k) {
        YearMonth month = YearMonth.from(first).plusMonths(k);
        return month.atDay(Math.min(first.getDayOfMonth(), month.lengthOfMonth()));
    }
}
