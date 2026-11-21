package gachi.chagok_server.domain.deadline.entity;

import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 서류에 붙은 기한. 캘린더·기한 알림·D-day가 모두 이 기한을 쓴다.
 * 매월 반복 기한은 한 행이 여러 회차를 뜻하며, date는 첫 회차다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deadlines", indexes = {
        @Index(name = "idx_deadlines_user", columnList = "user_id"),
        @Index(name = "idx_deadlines_document", columnList = "document_id")
})
public class Deadline extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadlineType type;

    /** 근거가 된 서류 항목 key (사용자가 만든 기한이면 null) */
    @Column(length = 60)
    private String fieldKey;

    @Column(nullable = false, length = 60)
    private String label;

    /** 기한 날짜 (매월 반복이면 첫 회차) */
    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Recurrence recurrence;

    private LocalDate endDate;

    private Long amount;

    /** AI가 추천한 알림 간격(쉼표로 구분). 알림 예약에는 쓰지 않고 서류 상세에만 보여 준다. */
    @Column(length = 60)
    private String suggestedAdvanceDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeadlineSource source;

    @Column(nullable = false)
    private boolean userEdited;

    /** 삭제 표시. AI 기한은 지운 기록을 남겨 다시 분석해도 되살리지 않는다. */
    @Column(nullable = false)
    private boolean deleted;

    /** 사용자가 알림을 껐으면 true. 서류를 다시 분석해도 알림을 자동으로 다시 만들지 않는다. */
    @Column(nullable = false)
    private boolean reminderDisabled;

    private Deadline(User user, Document document, DeadlineType type, String fieldKey, String label, LocalDate date,
                     Recurrence recurrence, LocalDate endDate, Long amount, DeadlineSource source) {
        this.user = user;
        this.document = document;
        this.type = type;
        this.fieldKey = fieldKey;
        this.label = label;
        this.date = date;
        this.recurrence = recurrence;
        this.endDate = endDate;
        this.amount = amount;
        this.source = source;
    }

    public static Deadline fromAi(User user, Document document, DeadlineType type, String fieldKey, String label,
                                  LocalDate date, Recurrence recurrence, LocalDate endDate, Long amount,
                                  List<Integer> suggestedAdvanceDays) {
        Deadline deadline = new Deadline(user, document, type, fieldKey, label, date, recurrence, endDate, amount, DeadlineSource.AI);
        deadline.suggestedAdvanceDays = suggestedAdvanceDays == null || suggestedAdvanceDays.isEmpty()
                ? null
                : String.join(",", suggestedAdvanceDays.stream().map(String::valueOf).toList());
        return deadline;
    }

    public static Deadline byUser(User user, Document document, DeadlineType type, String label, LocalDate date,
                                  Recurrence recurrence, LocalDate endDate, Long amount) {
        return new Deadline(user, document, type, null, label, date, recurrence, endDate, amount, DeadlineSource.USER);
    }

    /** 보낸 값(null이 아닌 것)만 바꾼다. AI 기한이면 userEdited=true가 된다. */
    public void change(String label, LocalDate date, DeadlineType type, Recurrence recurrence, LocalDate endDate, Long amount) {
        if (label != null) {
            this.label = label;
        }
        if (date != null) {
            this.date = date;
        }
        if (type != null) {
            this.type = type;
        }
        if (recurrence != null) {
            this.recurrence = recurrence;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (source == DeadlineSource.AI) {
            this.userEdited = true;
        }
    }

    /** 서류 항목을 고친 결과로 날짜·금액을 맞춘다 (사용자가 기한을 직접 고친 것은 아니다). */
    public void syncFromField(LocalDate date, Long amount) {
        if (date != null) {
            this.date = date;
        }
        if (amount != null) {
            this.amount = amount;
        }
    }

    public void disableReminder() {
        this.reminderDisabled = true;
    }

    public void enableReminder() {
        this.reminderDisabled = false;
    }

    /** USER 기한은 지우고, AI 기한은 표시만 남긴다. */
    public void markDeleted() {
        this.deleted = true;
    }

    public boolean isRecurring() {
        return recurrence != null;
    }

    public List<Integer> suggestedAdvanceDayList() {
        if (suggestedAdvanceDays == null || suggestedAdvanceDays.isBlank()) {
            return List.of();
        }
        return Arrays.stream(suggestedAdvanceDays.split(",")).map(Integer::valueOf).toList();
    }
}
