package gachi.chagok_server.domain.deadline.entity;

import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * 기한 알림 예약. 기한 하나에 알림 하나다.
 * 스케줄러가 nextAlertAt이 지난 알림을 찾아 DEADLINE_DUE 알림을 만든다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reminders",
        uniqueConstraints = @UniqueConstraint(name = "uk_reminders_deadline", columnNames = "deadline_id"),
        indexes = @Index(name = "idx_reminders_next_alert", columnList = "nextAlertAt"))
public class Reminder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deadline_id")
    private Deadline deadline;

    /** 알림 시각 (10분 단위) */
    @Column(nullable = false)
    private LocalTime alertTime;

    /** 며칠 전에 알릴지 (쉼표로 구분, 큰 값부터. 0은 당일) */
    @Column(nullable = false, length = 40)
    private String advanceDays;

    /** 매월 반복 기한에서 계속 보낼지. false면 onceDate 회차 한 번만 보낸다. */
    @Column(nullable = false)
    private boolean repeatMonthly;

    /** repeatMonthly=false인 반복 기한에서 알림을 보낼 회차 날짜 */
    private LocalDate onceDate;

    /** 다음에 보낼 알림 일시. 더 보낼 알림이 없으면 null */
    private Instant nextAlertAt;

    public Reminder(Deadline deadline, LocalTime alertTime, List<Integer> advanceDays, boolean repeatMonthly, LocalDate onceDate) {
        this.deadline = deadline;
        this.alertTime = alertTime;
        this.advanceDays = join(advanceDays);
        this.repeatMonthly = repeatMonthly;
        this.onceDate = onceDate;
    }

    public void change(LocalTime alertTime, List<Integer> advanceDays, Boolean repeatMonthly, LocalDate onceDate) {
        if (alertTime != null) {
            this.alertTime = alertTime;
        }
        if (advanceDays != null) {
            this.advanceDays = join(advanceDays);
        }
        if (repeatMonthly != null) {
            this.repeatMonthly = repeatMonthly;
            this.onceDate = repeatMonthly ? null : onceDate;
        }
    }

    public void scheduleNext(Instant nextAlertAt) {
        this.nextAlertAt = nextAlertAt;
    }

    public List<Integer> advanceDayList() {
        return Arrays.stream(advanceDays.split(",")).map(Integer::valueOf).toList();
    }

    private static String join(List<Integer> days) {
        return String.join(",", days.stream().map(String::valueOf).toList());
    }
}
