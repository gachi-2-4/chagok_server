package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.ReminderCreateRequest;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.ReminderResponse;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.ReminderUpdateRequest;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSchedule;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * 기한 알림 예약. 기한이 생기면 자동으로 예약하고, 사용자가 켜고 끄고 바꿀 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderService {

    private static final LocalTime FALLBACK_ALERT_TIME = LocalTime.of(9, 0);

    private final ReminderRepository reminders;
    private final DeadlineRepository deadlines;
    private final NotificationSettingsRepository settings;
    private final DeadlineAssembler assembler;

    /** 기한 알림 등록 (껐던 알림을 다시 켠다). */
    @Transactional
    public ReminderResponse create(Long userId, ReminderCreateRequest request) {
        Deadline deadline = deadlines.findByIdAndUserIdAndDeletedFalse(request.deadlineId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEADLINE_NOT_FOUND));
        if (reminders.findByDeadlineId(deadline.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.REMINDER_ALREADY_EXISTS);
        }
        LocalDate today = KstTime.today();
        Optional<LocalDate> nextOccurrence = DeadlineSchedule.nextOnOrAfter(deadline, today);
        if (nextOccurrence.isEmpty()) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED);
        }

        LocalTime alertTime = request.alertTime() != null ? parseAlertTime(request.alertTime()) : defaultAlertTime(userId);
        List<Integer> advanceDays = request.advanceDays() != null
                ? normalizeAdvanceDays(request.advanceDays())
                : DeadlineSchedule.defaultAdvanceDays(deadline.getType(), deadline.isRecurring());
        boolean repeatMonthly = request.repeatMonthly() == null || request.repeatMonthly();

        Reminder reminder = new Reminder(deadline, alertTime, advanceDays, repeatMonthly,
                deadline.isRecurring() && !repeatMonthly ? nextOccurrence.get() : null);
        schedule(reminder);
        deadline.enableReminder();
        return assembler.reminderResponse(reminders.saveAndFlush(reminder));
    }

    @Transactional
    public ReminderResponse update(Long userId, Long reminderId, ReminderUpdateRequest request) {
        Reminder reminder = reminders.findOwned(reminderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REMINDER_NOT_FOUND));
        Deadline deadline = reminder.getDeadline();

        LocalTime alertTime = request.alertTime() != null ? parseAlertTime(request.alertTime()) : null;
        List<Integer> advanceDays = request.advanceDays() != null ? normalizeAdvanceDays(request.advanceDays()) : null;
        LocalDate onceDate = null;
        if (request.repeatMonthly() != null && !request.repeatMonthly() && deadline.isRecurring()) {
            onceDate = DeadlineSchedule.nextOnOrAfter(deadline, KstTime.today()).orElse(null);
        }
        reminder.change(alertTime, advanceDays, request.repeatMonthly(), onceDate);
        schedule(reminder);
        return assembler.reminderResponse(reminder);
    }

    /** 기한 알림을 끈다. 끈 기한은 서류를 다시 분석해도 알림을 자동으로 다시 만들지 않는다. */
    @Transactional
    public void delete(Long userId, Long reminderId) {
        Reminder reminder = reminders.findOwned(reminderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REMINDER_NOT_FOUND));
        reminder.getDeadline().disableReminder();
        reminders.delete(reminder);
    }

    /**
     * 기한이 생겼을 때 기한 종류별 기본값으로 알림을 자동 예약한다.
     * 사용자가 껐던 기한이거나 보낼 알림이 남아 있지 않으면 만들지 않는다.
     */
    @Transactional
    public Optional<Reminder> autoCreate(Deadline deadline) {
        if (deadline.isReminderDisabled() || reminders.findByDeadlineId(deadline.getId()).isPresent()) {
            return Optional.empty();
        }
        Reminder reminder = new Reminder(deadline, defaultAlertTime(deadline.getUser().getId()),
                DeadlineSchedule.defaultAdvanceDays(deadline.getType(), deadline.isRecurring()), true, null);
        schedule(reminder);
        if (reminder.getNextAlertAt() == null) {
            return Optional.empty();
        }
        return Optional.of(reminders.save(reminder));
    }

    /** 기한 날짜·종류가 바뀌었을 때 아직 보내지 않은 알림 예약을 다시 계산한다. */
    @Transactional
    public void reschedule(Deadline deadline) {
        reminders.findByDeadlineId(deadline.getId()).ifPresent(reminder -> {
            if (!deadline.isRecurring()) {
                reminder.change(null, null, true, null);
            }
            schedule(reminder);
        });
    }

    /** 서류를 지울 때 그 서류 기한의 알림을 모두 지운다. */
    @Transactional
    public void deleteByDocument(Long documentId) {
        reminders.deleteByDocumentId(documentId);
    }

    /** 다음 알림 일시를 다시 계산해 저장 대상에 넣는다. */
    public void schedule(Reminder reminder) {
        reminder.scheduleNext(DeadlineSchedule.nextAlert(reminder.getDeadline(), reminder, Instant.now()).orElse(null));
    }

    private LocalTime defaultAlertTime(Long userId) {
        return settings.findById(userId).map(s -> s.getDefaultAlertTime()).orElse(FALLBACK_ALERT_TIME);
    }

    private LocalTime parseAlertTime(String raw) {
        try {
            LocalTime time = LocalTime.parse(raw);
            if (time.getMinute() % 10 != 0 || time.getSecond() != 0) {
                throw new BusinessException(ErrorCode.INVALID_ALERT_TIME);
            }
            return time;
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_ALERT_TIME);
        }
    }

    /** 0–90 사이 정수 1–5개. 중복은 합치고 큰 값부터 정렬한다. */
    private List<Integer> normalizeAdvanceDays(List<Integer> raw) {
        List<Integer> days = raw.stream().filter(d -> d != null).distinct().sorted((a, b) -> b - a).toList();
        if (days.isEmpty() || days.size() > 5 || days.stream().anyMatch(d -> d < 0 || d > 90)) {
            throw new BusinessException(ErrorCode.INVALID_ADVANCE_DAYS);
        }
        return days;
    }
}
