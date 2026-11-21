package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Conditions;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.event.DeadlineEditedEvent;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.Detail;
import gachi.chagok_server.domain.document.dto.DocumentDtos.FieldEdit;
import gachi.chagok_server.domain.document.dto.DocumentDtos.FieldsRequest;
import gachi.chagok_server.domain.document.dto.DocumentDtos.ScheduleEdit;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.event.SummaryCardRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentDetailAssembler;
import gachi.chagok_server.domain.document.support.DocumentFieldValues;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 서류 내용 수정. AI가 잘못 읽은 항목 값·납부일을 고치거나, 서류 종류를 바꿔 다시 분석한다.
 * 고친 값은 기한·알림, 요약 카드, AI 채팅이 같은 값을 쓰도록 바로 반영한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentFieldService {

    static final int MIN_DAY_OF_MONTH = 1;
    static final int MAX_DAY_OF_MONTH = 31;

    private final DocumentRepository documents;
    private final DocumentAnalysisResultRepository results;
    private final DeadlineRepository deadlines;
    private final ReminderRepository reminders;
    private final ReminderService reminderService;
    private final ComparisonRepository comparisons;
    private final DocumentDetailAssembler assembler;
    private final DocumentJson json;
    private final ApplicationEventPublisher events;

    @Transactional
    public Detail edit(Long userId, Long documentId, FieldsRequest request) {
        Document document = documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (!document.isAnalyzed()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        boolean hasFieldEdits = request.fields() != null && !request.fields().isEmpty();
        boolean hasScheduleEdits = request.paymentSchedules() != null && !request.paymentSchedules().isEmpty();

        if (request.docType() != null) {
            // 서류 종류를 바꾸면 항목 구성이 달라지므로 다시 분석한다. 다른 수정과 함께 보낼 수 없다.
            if (hasFieldEdits || hasScheduleEdits) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            document.restartAnalysis();
            comparisons.markOutdatedByDocument(documentId);
            events.publishEvent(new DocumentAnalysisRequestedEvent(documentId, userId, null, request.docType().name()));
            return assembler.assemble(document);
        }

        DocumentAnalysisResult result = results.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_READY));
        Analysis before = json.readAnalysis(result.getAnalysisJson());

        List<Field> fields = new ArrayList<>(before.fields() == null ? List.of() : before.fields());
        Set<String> changedFields = new HashSet<>();
        if (hasFieldEdits) {
            for (FieldEdit edit : request.fields()) {
                int index = indexOf(fields, edit.key());
                Field field = fields.get(index);
                fields.set(index, field.edited(DocumentFieldValues.validate(field.valueType(), edit.value())));
                changedFields.add(edit.key());
            }
        }

        List<PaymentSchedule> schedules = new ArrayList<>(scheduleList(before));
        Set<String> changedSchedules = new HashSet<>();
        if (hasScheduleEdits) {
            for (ScheduleEdit edit : request.paymentSchedules()) {
                int index = scheduleIndexOf(schedules, edit.key());
                if (edit.dayOfMonth() != null) {
                    if (edit.dayOfMonth() < MIN_DAY_OF_MONTH || edit.dayOfMonth() > MAX_DAY_OF_MONTH) {
                        throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
                    }
                    schedules.set(index, schedules.get(index).withDayOfMonth(edit.dayOfMonth()));
                    changedSchedules.add(edit.key());
                }
            }
        }

        Analysis after = before.withFields(fields, new Conditions(schedules));
        save(document, result, after);
        recalculateDeadlines(document, after, changedFields, changedSchedules);
        events.publishEvent(new SummaryCardRequestedEvent(documentId, userId));
        documents.flush();
        return assembler.assemble(document);
    }

    /**
     * 사용자가 AI 기한을 고쳤을 때, 기한이 나온 서류 항목도 같이 고친다 (서류 상세·AI 정리본·AI 채팅이 같은 값을 쓰도록).
     * 기한 자체는 이미 고쳐졌으므로 여기서 기한을 다시 계산하지 않는다.
     */
    @EventListener
    @Transactional
    public void onDeadlineEdited(DeadlineEditedEvent event) {
        Document document = documents.findById(event.documentId()).orElse(null);
        DocumentAnalysisResult result = results.findById(event.documentId()).orElse(null);
        if (document == null || result == null || result.getAnalysisJson() == null) {
            return;
        }
        Analysis before = json.readAnalysis(result.getAnalysisJson());
        List<Field> fields = new ArrayList<>(before.fields() == null ? List.of() : before.fields());
        List<PaymentSchedule> schedules = new ArrayList<>(scheduleList(before));
        boolean changed = false;

        int fieldIndex = indexOrMinus(fields, event.fieldKey());
        if (fieldIndex >= 0) {
            Field field = fields.get(fieldIndex);
            if (event.newDate() != null && event.type() == DeadlineType.EXPIRY && "DATE".equals(field.valueType())) {
                // 예) "계약 만료" 날짜 → contractEndDate 항목
                fields.set(fieldIndex, field.edited(event.newDate().toString()));
                changed = true;
            }
            if (event.newAmount() != null && "AMOUNT".equals(field.valueType())) {
                fields.set(fieldIndex, field.edited(event.newAmount()));
                changed = true;
            }
        }
        if (event.newDate() != null && event.recurring() && event.type() == DeadlineType.PAYMENT) {
            // 예) "월세 납부" 날짜 → paymentSchedules의 dayOfMonth
            int scheduleIndex = scheduleIndexOrMinus(schedules, event.fieldKey());
            if (scheduleIndex >= 0) {
                schedules.set(scheduleIndex, schedules.get(scheduleIndex).withDayOfMonth(event.newDate().getDayOfMonth()));
                changed = true;
            }
        }
        if (changed) {
            save(document, result, before.withFields(fields, new Conditions(schedules)));
            events.publishEvent(new SummaryCardRequestedEvent(event.documentId(), event.userId()));
        }
    }

    private void save(Document document, DocumentAnalysisResult result, Analysis analysis) {
        result.updateAnalysis(json.write(analysis), DocumentFieldValues.searchText(document.getTitle(), analysis));
        document.updateNeedsReview(DocumentFieldValues.anyNeedsReview(analysis.fields()));
        // 이 서류를 쓴 비교 결과는 다시 비교하라고 안내한다.
        comparisons.markOutdatedByDocument(document.getId());
    }

    /**
     * 고친 항목에 맞춰 AI 기한을 다시 계산한다.
     * 만료 날짜·금액·매월 납부일을 바꾸면 기한이 바뀌고 그 기한의 알림 예약도 다시 계산한다.
     * 값을 비워서 사라진 기한은 알림도 같이 지운다.
     */
    private void recalculateDeadlines(Document document, Analysis analysis, Set<String> changedFields, Set<String> changedSchedules) {
        for (Deadline deadline : deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(document.getId())) {
            if (deadline.getSource() != DeadlineSource.AI || deadline.getFieldKey() == null) {
                continue;
            }
            String key = deadline.getFieldKey();
            boolean fieldChanged = changedFields.contains(key);
            boolean scheduleChanged = changedSchedules.contains(key);
            if (!fieldChanged && !scheduleChanged) {
                continue;
            }

            Field field = DocumentFieldValues.find(analysis, key).orElse(null);
            if (fieldChanged && deadline.getType() == DeadlineType.EXPIRY && field != null && "DATE".equals(field.valueType())) {
                if (field.value() == null) {
                    reminders.findByDeadlineId(deadline.getId()).ifPresent(reminders::delete);
                    deadlines.delete(deadline);
                    continue;
                }
                deadline.syncFromField(LocalDate.parse(String.valueOf(field.value())), null);
            }
            if (fieldChanged && deadline.getType() == DeadlineType.PAYMENT) {
                DocumentFieldValues.amountOf(analysis, key).ifPresent(amount -> deadline.syncFromField(null, amount));
            }
            if (scheduleChanged && deadline.getType() == DeadlineType.PAYMENT && deadline.isRecurring()) {
                int day = analysis.conditions().paymentSchedules().stream()
                        .filter(s -> key.equals(s.fieldKey())).findFirst().map(PaymentSchedule::dayOfMonth).orElse(0);
                if (day > 0) {
                    deadline.syncFromField(withDay(deadline.getDate(), day), null);
                }
            }
            reminderService.reschedule(deadline);
        }
    }

    /** 그 달에 없는 날이면(예: 31일) 말일로 처리한다. */
    static LocalDate withDay(LocalDate date, int dayOfMonth) {
        YearMonth month = YearMonth.from(date);
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private static List<PaymentSchedule> scheduleList(Analysis analysis) {
        return analysis.conditions() == null || analysis.conditions().paymentSchedules() == null
                ? List.of() : analysis.conditions().paymentSchedules();
    }

    private static int indexOf(List<Field> fields, String key) {
        int index = indexOrMinus(fields, key);
        if (index < 0) {
            throw new BusinessException(ErrorCode.UNKNOWN_FIELD_KEY);
        }
        return index;
    }

    private static int indexOrMinus(List<Field> fields, String key) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).key() != null && fields.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static int scheduleIndexOf(List<PaymentSchedule> schedules, String key) {
        int index = scheduleIndexOrMinus(schedules, key);
        if (index < 0) {
            throw new BusinessException(ErrorCode.UNKNOWN_FIELD_KEY);
        }
        return index;
    }

    private static int scheduleIndexOrMinus(List<PaymentSchedule> schedules, String key) {
        for (int i = 0; i < schedules.size(); i++) {
            if (schedules.get(i).fieldKey() != null && schedules.get(i).fieldKey().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
