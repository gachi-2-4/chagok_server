package gachi.chagok_server.domain.deadline.dto;

import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 기한·기한 알림 API 요청·응답 DTO.
 */
public final class DeadlineDtos {

    private DeadlineDtos() {
    }

    public record DocumentRef(Long documentId, String title) {
    }

    public record BoxRef(Long boxId, String name) {
    }

    /** 기한에 걸린 알림의 요약 (알림 시각은 HH:mm) */
    public record ReminderSummary(Long reminderId, String alertTime, List<Integer> advanceDays) {
    }

    // ---- 기한 ----

    public record ListItem(Long deadlineId, LocalDate date, long dDay, DeadlineType type, String label, Long amount,
                           DocumentRef document, BoxRef box, DeadlineSource source, boolean isCompleted, Long reminderId) {
    }

    public record ListResponse(LocalDate from, LocalDate to, List<ListItem> content) {
    }

    public record CreateRequest(@NotNull Long documentId, @NotNull DeadlineType type, String label, LocalDate date,
                                Recurrence recurrence, LocalDate endDate, Long amount) {
    }

    /** 보낸 값만 바꾼다. null이면 바꾸지 않는다. */
    public record UpdateRequest(String label, LocalDate date, DeadlineType type, Recurrence recurrence,
                                LocalDate endDate, Long amount) {
    }

    /** 기한 추가·수정 응답 */
    public record Response(Long deadlineId, Long documentId, String documentTitle, DeadlineType type, String label,
                           LocalDate date, Recurrence recurrence, LocalDate endDate, Long amount, long dDay,
                           DeadlineSource source, boolean userEdited, boolean isCompleted, ReminderSummary reminder,
                           OffsetDateTime createdAt) {
    }

    /** 서류 상세의 deadlines[] 한 줄 */
    public record DocumentDeadline(Long deadlineId, DeadlineType type, String fieldKey, String label, LocalDate date,
                                   Recurrence recurrence, LocalDate endDate, Long amount, long dDay,
                                   List<Integer> suggestedAdvanceDays, DeadlineSource source, boolean userEdited,
                                   boolean isCompleted, ReminderSummary reminder) {
    }

    /** 서류 상세의 primaryDeadline·서류 확인 응답의 primaryDeadline */
    public record PrimaryDeadline(Long deadlineId, DeadlineType type, String label, LocalDate date, long dDay,
                                  boolean hasReminder) {
    }

    // ---- 기한 알림 ----

    public record ReminderCreateRequest(@NotNull Long deadlineId, String alertTime, List<Integer> advanceDays,
                                        Boolean repeatMonthly) {
    }

    public record ReminderUpdateRequest(String alertTime, List<Integer> advanceDays, Boolean repeatMonthly) {
    }

    public record ReminderDeadline(Long deadlineId, DeadlineType type, String label, LocalDate date,
                                   Recurrence recurrence, Long amount, DocumentRef document) {
    }

    public record ReminderResponse(Long reminderId, ReminderDeadline deadline, String alertTime, List<Integer> advanceDays,
                                   boolean repeatMonthly, LocalDate endDate, OffsetDateTime nextAlertAt,
                                   OffsetDateTime createdAt) {
    }
}
