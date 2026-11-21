package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.*;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.event.DeadlineEditedEvent;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineOccurrence;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 캘린더 기한 조회·추가·수정·삭제.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeadlineService {

    static final int MAX_LABEL_LENGTH = 30;
    /** 조회 기간은 최대 1년 (윤년 366일 포함) */
    static final int MAX_RANGE_DAYS = 366;

    private final DeadlineRepository deadlines;
    private final ReminderRepository reminders;
    private final DocumentRepository documents;
    private final DeadlineQueryService query;
    private final ReminderService reminderService;
    private final DeadlineAssembler assembler;
    private final ApplicationEventPublisher events;

    public ListResponse list(Long userId, LocalDate from, LocalDate to, boolean includeCompleted, Long boxId, Long documentId) {
        LocalDate start = from != null ? from : KstTime.today();
        LocalDate end = to != null ? to : start.plusDays(30);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }

        DeadlineSnapshot snapshot = query.snapshot(userId);
        List<ListItem> content = snapshot.occurrences(start, end, includeCompleted, boxId, documentId).stream()
                .map(o -> toListItem(o, snapshot.today()))
                .toList();
        return new ListResponse(start, end, content);
    }

    @Transactional
    public Response create(Long userId, CreateRequest request) {
        Document document = documents.findByIdAndUserId(request.documentId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (!document.isAnalyzed()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        String label = validLabel(request.label());
        if (request.date() == null || request.amount() != null && request.amount() < 0
                || request.endDate() != null && request.endDate().isBefore(request.date())) {
            throw new BusinessException(ErrorCode.INVALID_DEADLINE);
        }

        Deadline deadline = deadlines.saveAndFlush(Deadline.byUser(document.getUser(), document, request.type(), label,
                request.date(), request.recurrence(), request.recurrence() == null ? null : request.endDate(), request.amount()));
        Reminder reminder = reminderService.autoCreate(deadline).orElse(null);
        return assembler.response(deadline, reminder, query.completedKeys(userId));
    }

    @Transactional
    public Response update(Long userId, Long deadlineId, UpdateRequest request) {
        Deadline deadline = find(userId, deadlineId);
        String label = request.label() != null ? validLabel(request.label()) : null;
        if (request.amount() != null && request.amount() < 0) {
            throw new BusinessException(ErrorCode.INVALID_DEADLINE);
        }
        LocalDate newDate = request.date();
        deadline.change(label, newDate, request.type(), request.recurrence(), request.endDate(), request.amount());
        if (deadline.getEndDate() != null && deadline.getEndDate().isBefore(deadline.getDate())) {
            throw new BusinessException(ErrorCode.INVALID_DEADLINE);
        }

        reminderService.reschedule(deadline);
        // AI 기한이 서류 항목에서 나온 경우, 그 항목도 같이 고친다 (서류 쪽이 이벤트를 받아 처리).
        if (deadline.getSource() == DeadlineSource.AI && deadline.getFieldKey() != null
                && (newDate != null || request.amount() != null)) {
            events.publishEvent(new DeadlineEditedEvent(userId, deadline.getDocument().getId(), deadline.getFieldKey(),
                    deadline.getType(), deadline.isRecurring(), newDate, request.amount()));
        }
        Reminder reminder = reminders.findByDeadlineId(deadlineId).orElse(null);
        return assembler.response(deadline, reminder, query.completedKeys(userId));
    }

    @Transactional
    public void delete(Long userId, Long deadlineId) {
        Deadline deadline = find(userId, deadlineId);
        reminders.findByDeadlineId(deadlineId).ifPresent(reminders::delete);
        if (deadline.getSource() == DeadlineSource.AI) {
            // 서류를 다시 분석해도 되살리지 않도록 지운 기록을 남긴다.
            deadline.markDeleted();
        } else {
            deadlines.delete(deadline);
        }
    }

    private Deadline find(Long userId, Long deadlineId) {
        return deadlines.findByIdAndUserIdAndDeletedFalse(deadlineId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEADLINE_NOT_FOUND));
    }

    private String validLabel(String raw) {
        String label = raw == null ? "" : raw.trim();
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_DEADLINE);
        }
        return label;
    }

    private ListItem toListItem(DeadlineOccurrence occurrence, LocalDate today) {
        Deadline deadline = occurrence.deadline();
        Document document = deadline.getDocument();
        return new ListItem(deadline.getId(), occurrence.date(), KstTime.dDay(today, occurrence.date()), deadline.getType(),
                deadline.getLabel(), deadline.getAmount(), new DocumentRef(document.getId(), document.getTitle()),
                new BoxRef(document.getBox().getId(), document.getBox().getName()), deadline.getSource(),
                occurrence.completed(), occurrence.reminderId());
    }
}
