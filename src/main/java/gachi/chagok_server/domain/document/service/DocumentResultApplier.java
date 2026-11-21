package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.CalendarEvent;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Conditions;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.KeyCost;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Priority;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.AiDeadlineMapper;
import gachi.chagok_server.domain.document.support.DeadlineFieldSync;
import gachi.chagok_server.domain.document.support.DocumentFieldValues;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 문서 분석 응답을 서류에 반영한다: 분석 결과 저장, 서류 정보 채우기, 기한·알림 만들기, 완료 알림.
 */
@Service
@RequiredArgsConstructor
public class DocumentResultApplier {

    private static final int MAX_TITLE_LENGTH = 50;

    private final DocumentRepository documents;
    private final DocumentAnalysisResultRepository results;
    private final DeadlineRepository deadlines;
    private final ReminderRepository reminders;
    private final ReminderService reminderService;
    private final NotificationPublisher publisher;
    private final DocumentJson json;

    /** 분석 중(ANALYZING)인 서류에만 반영한다. 그 사이 서류가 지워졌거나 상태가 바뀌었으면 아무것도 하지 않는다. */
    @Transactional
    public void apply(Long documentId, Analysis analysis) {
        apply(documentId, analysis, null);
    }

    /**
     * @param restoreStatus 복구용 재분석이면 그 전의 서류 상태. 이때는 사용자가 고친 값(항목·납부일·기한·알림 설정)을
     *                      새 결과에 이어 붙이고, 끝나면 이 상태로 되돌리며, 완료 알림은 보내지 않는다. 일반 분석이면 null.
     */
    @Transactional
    public void apply(Long documentId, Analysis analysis, DocumentStatus restoreStatus) {
        Document document = documents.findById(documentId).orElse(null);
        if (document == null || document.getStatus() != DocumentStatus.ANALYZING) {
            return;
        }
        DocumentAnalysisResult result = results.findById(documentId).orElseGet(() -> new DocumentAnalysisResult(document));

        Analysis incoming = analysis;
        Recovery recovery = null;
        if (restoreStatus != null && result.getAnalysisJson() != null) {
            recovery = carryOverEdits(json.readAnalysis(result.getAnalysisJson()), analysis);
            incoming = recovery.merged();
        }

        // 원문(fullText)은 AI가 나중에 내부 API로 다시 가져가므로 따로 저장하고, 저장하는 JSON에서는 뺀다.
        Analysis stored = incoming.withoutFullText();
        String title = titleOf(analysis, document);
        result.replace(json.write(stored), analysis.fullText(), DocumentFieldValues.searchText(title, stored));
        results.save(result);

        Priority priority = analysis.priority();
        KeyCost keyCost = priority == null ? null : priority.keyCost();
        document.completeAnalysis(title, parse(DocType.class, analysis.docType(), DocType.ETC),
                parse(DocCategory.class, analysis.docCategory(), DocCategory.INFO), analysis.summary(),
                priority == null ? null : parse(PriorityLevel.class, priority.level(), PriorityLevel.LOW),
                priority == null ? null : priority.score(),
                keyCost == null ? null : keyCost.label(), keyCost == null ? null : keyCost.amount(),
                DocumentFieldValues.anyNeedsReview(stored.fields()));

        replaceDeadlines(document, stored, recovery);

        if (restoreStatus != null) {
            // 백그라운드 복구라 사용자는 이미 확인한 서류다. 원래 상태로 돌려 놓고 알림은 보내지 않는다.
            document.restoreStatus(restoreStatus);
            return;
        }
        publisher.publish(document.getUser(), NotificationType.ANALYSIS_COMPLETED, "서류 분석이 끝났어요",
                "'" + document.getTitle() + "' 분석이 끝났어요. 읽어 낸 내용을 확인해 주세요.",
                n -> n.withDocument(document.getId(), document.getTitle(), document.getBox().getName()));
    }

    /** 복구용 재분석에서 이어 붙일 사용자 수정 */
    private record Recovery(Analysis merged, Set<String> editedFields, Set<String> editedSchedules) {
    }

    /** 이전 분석에서 사용자가 고친 항목 값·납부일을 새 분석 결과에 이어 붙인다 (AI가 새로 읽은 값은 aiValue로 보관). */
    private Recovery carryOverEdits(Analysis previous, Analysis fresh) {
        Set<String> editedFields = new HashSet<>();
        List<Field> fields = new ArrayList<>();
        for (Field field : fresh.fields() == null ? List.<Field>of() : fresh.fields()) {
            Field before = DocumentFieldValues.find(previous, field.key()).orElse(null);
            if (before != null && Boolean.TRUE.equals(before.userEdited())) {
                fields.add(field.edited(before.value()));
                editedFields.add(field.key());
            } else {
                fields.add(field);
            }
        }

        Set<String> editedSchedules = new HashSet<>();
        List<PaymentSchedule> schedules = new ArrayList<>();
        List<PaymentSchedule> previousSchedules = previous.conditions() == null || previous.conditions().paymentSchedules() == null
                ? List.of() : previous.conditions().paymentSchedules();
        List<PaymentSchedule> freshSchedules = fresh.conditions() == null || fresh.conditions().paymentSchedules() == null
                ? List.of() : fresh.conditions().paymentSchedules();
        for (PaymentSchedule schedule : freshSchedules) {
            PaymentSchedule before = previousSchedules.stream()
                    .filter(p -> p.fieldKey() != null && p.fieldKey().equals(schedule.fieldKey())
                            && Boolean.TRUE.equals(p.userEdited()) && p.dayOfMonth() != null)
                    .findFirst().orElse(null);
            if (before != null) {
                schedules.add(schedule.withDayOfMonth(before.dayOfMonth()));
                editedSchedules.add(schedule.fieldKey());
            } else {
                schedules.add(schedule);
            }
        }
        return new Recovery(fresh.withFields(fields, new Conditions(schedules)), editedFields, editedSchedules);
    }

    /** 이전 알림 설정. 복구 재분석에서 새 기한에 다시 입혀 준다. */
    private record ReminderConfig(LocalTime alertTime, List<Integer> advanceDays, boolean repeatMonthly) {
    }

    /**
     * 분석 결과로 AI 기한을 새로 만든다. 다시 분석하는 경우 이전 AI 기한은 지우고 새로 만들되,
     * 사용자가 지운 AI 기한은 되살리지 않고 사용자가 끈 알림은 다시 켜지 않는다. 사용자가 추가한 기한은 그대로 둔다.
     * 복구용 재분석이면 사용자가 고친 항목·기한·알림 설정도 이어 붙인다.
     */
    private void replaceDeadlines(Document document, Analysis analysis, Recovery recovery) {
        Set<String> removedByUser = new HashSet<>();
        Set<String> reminderOff = new HashSet<>();
        Map<String, Deadline> editedBefore = new HashMap<>();
        Map<String, ReminderConfig> reminderBefore = new HashMap<>();
        List<Deadline> previous = deadlines.findByDocumentId(document.getId()).stream()
                .filter(d -> d.getSource() == DeadlineSource.AI)
                .toList();
        for (Deadline old : previous) {
            String key = AiDeadlineMapper.key(old.getType(), old.getFieldKey());
            if (old.isDeleted()) {
                removedByUser.add(key);
                continue; // 지운 기록은 남긴다.
            }
            if (old.isReminderDisabled()) {
                reminderOff.add(key);
            }
            if (old.isUserEdited()) {
                editedBefore.put(key, old);
            }
            var oldReminder = reminders.findByDeadlineId(old.getId());
            oldReminder.ifPresent(r -> reminderBefore.put(key, new ReminderConfig(r.getAlertTime(), r.advanceDayList(), r.isRepeatMonthly())));
            oldReminder.ifPresent(reminders::delete);
            deadlines.delete(old);
        }
        deadlines.flush();

        if (analysis.calendarEvents() == null) {
            return;
        }
        for (CalendarEvent event : analysis.calendarEvents()) {
            AiDeadlineMapper.toDeadline(document, analysis, event).ifPresent(deadline -> {
                String key = AiDeadlineMapper.key(deadline.getType(), deadline.getFieldKey());
                if (removedByUser.contains(key)) {
                    return;
                }
                if (recovery != null && !carryOverDeadlineEdits(deadline, key, analysis, recovery, editedBefore)) {
                    return;
                }
                if (reminderOff.contains(key)) {
                    deadline.disableReminder();
                }
                deadlines.saveAndFlush(deadline);
                reminderService.autoCreate(deadline).ifPresent(reminder -> restoreReminderConfig(reminder, deadline, reminderBefore.get(key)));
            });
        }
    }

    /**
     * 고친 항목에 맞춰 새 기한의 날짜·금액을 맞추고, 사용자가 기한을 직접 고친 값은 그대로 입힌다.
     *
     * @return false면 항목을 비워서 기한이 사라져야 한다
     */
    private boolean carryOverDeadlineEdits(Deadline deadline, String key, Analysis analysis, Recovery recovery,
                                           Map<String, Deadline> editedBefore) {
        String fieldKey = deadline.getFieldKey();
        DeadlineFieldSync.Result result = DeadlineFieldSync.apply(deadline, analysis,
                fieldKey != null && recovery.editedFields().contains(fieldKey),
                fieldKey != null && recovery.editedSchedules().contains(fieldKey));
        if (result == DeadlineFieldSync.Result.REMOVED) {
            return false;
        }
        Deadline edited = editedBefore.get(key);
        if (edited != null) {
            deadline.change(edited.getLabel(), edited.getDate(), null, null, edited.getEndDate(), edited.getAmount(), false, false, false);
        }
        return true;
    }

    /** 이전 기한 알림의 시각·미리 알림 일수를 새 알림에 그대로 입힌다. */
    private void restoreReminderConfig(Reminder reminder, Deadline deadline, ReminderConfig before) {
        // 매월 반복을 끄고 한 회차만 보내던 알림은 회차를 알 수 없어 기본값을 그대로 둔다.
        if (before == null || (deadline.isRecurring() && !before.repeatMonthly())) {
            return;
        }
        reminder.change(before.alertTime(), before.advanceDays(), null, null);
        reminderService.schedule(reminder);
    }

    /** 사용자가 이름을 직접 정한 서류는 그대로 두고, 아니면 AI가 준 제목(50자까지)을 쓴다. */
    private String titleOf(Analysis analysis, Document document) {
        String title = analysis.title() == null ? "" : analysis.title().trim();
        if (title.isEmpty()) {
            return document.getTitle();
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
