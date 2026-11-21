package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.KeyDeadline;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Priority;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Section;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.DocumentDeadline;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.PrimaryDeadline;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSchedule;
import gachi.chagok_server.domain.deadline.service.DeadlineAssembler;
import gachi.chagok_server.domain.deadline.service.DeadlineQueryService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.BoxRef;
import gachi.chagok_server.domain.document.dto.DocumentDtos.Detail;
import gachi.chagok_server.domain.document.dto.DocumentDtos.FieldView;
import gachi.chagok_server.domain.document.dto.DocumentDtos.PaymentScheduleView;
import gachi.chagok_server.domain.document.dto.DocumentDtos.SummaryCardView;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentCheckItem;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentCheckItemRepository;
import gachi.chagok_server.global.common.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 서류 상세 응답을 만든다. 저장해 둔 AI 분석 결과에 사용자가 고친 값, 기한, 체크 상태를 합친다.
 * 읽기 전용 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class DocumentDetailAssembler {

    private final DocumentAnalysisResultRepository results;
    private final DocumentCheckItemRepository checkItems;
    private final DeadlineRepository deadlines;
    private final ReminderRepository reminders;
    private final DeadlineQueryService deadlineQuery;
    private final DeadlineAssembler deadlineAssembler;
    private final DocumentJson json;

    public Detail assemble(Document document) {
        BoxRef box = new BoxRef(document.getBox().getId(), document.getBox().getName());
        Optional<DocumentAnalysisResult> stored = document.getStatus() == DocumentStatus.ANALYZING
                || document.getStatus() == DocumentStatus.FAILED
                ? Optional.empty()
                : results.findById(document.getId());

        if (stored.isEmpty() || stored.get().getAnalysisJson() == null) {
            return new Detail(document.getId(), document.getTitle(), document.getOriginalFileName(), box, document.getStatus(),
                    document.getAnalysisStep(), document.getFailReason(), document.getPageCount(), null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    KstTime.toOffset(document.getCreatedAt()), KstTime.toOffset(document.getUpdatedAt()), null);
        }

        DocumentAnalysisResult result = stored.get();
        Analysis analysis = json.readAnalysis(result.getAnalysisJson());
        List<Deadline> documentDeadlines = deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(document.getId());
        Map<Long, Reminder> reminderByDeadline = remindersOf(documentDeadlines);
        Set<String> completed = deadlineQuery.completedKeys(document.getUser().getId());

        List<DocumentDeadline> deadlineViews = documentDeadlines.stream()
                .map(d -> deadlineAssembler.documentDeadline(d, reminderByDeadline.get(d.getId()), completed))
                .toList();

        return new Detail(document.getId(), document.getTitle(), document.getOriginalFileName(), box, document.getStatus(),
                document.getAnalysisStep(), document.getFailReason(), document.getPageCount(), document.getDocType(),
                document.getDocCategory(), analysis.classificationReasons(), analysis.summary(),
                DocumentFieldValues.anyNeedsReview(analysis.fields()), analysis.parties(), fieldViews(analysis),
                scheduleViews(analysis), analysis.priority(),
                pickPrimary(documentDeadlines, analysis.priority(), reminderByDeadline, completed),
                deadlineViews, summaryCard(document.getId(), result), analysis.userRelevance(), analysis.legalCheck(),
                KstTime.toOffset(document.getCreatedAt()), KstTime.toOffset(document.getUpdatedAt()), null);
    }

    /** 서류 확인 응답이 쓰는 대표 기한. 기한이 없으면 null. */
    public PrimaryDeadline primaryDeadline(Document document) {
        Analysis analysis = results.findById(document.getId())
                .map(r -> json.readAnalysis(r.getAnalysisJson())).orElse(null);
        List<Deadline> documentDeadlines = deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(document.getId());
        return pickPrimary(documentDeadlines, analysis == null ? null : analysis.priority(),
                remindersOf(documentDeadlines), deadlineQuery.completedKeys(document.getUser().getId()));
    }

    private Map<Long, Reminder> remindersOf(List<Deadline> documentDeadlines) {
        if (documentDeadlines.isEmpty()) {
            return Map.of();
        }
        return reminders.findByDeadlineIdIn(documentDeadlines.stream().map(Deadline::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getDeadline().getId(), r -> r));
    }

    private List<FieldView> fieldViews(Analysis analysis) {
        if (analysis.fields() == null) {
            return List.of();
        }
        return analysis.fields().stream()
                .map(f -> new FieldView(f.key(), f.label(), f.originalLabel(), f.value(), f.valueType(), f.evidence(),
                        f.evidenceLocation(), f.confidence(), f.isNeedsReview(), Boolean.TRUE.equals(f.userEdited())))
                .toList();
    }

    /** 매월 납부 일정. 금액은 fieldKey로 항목에서 찾아 채운다 (사용자가 금액을 고치면 같이 바뀜). */
    private List<PaymentScheduleView> scheduleViews(Analysis analysis) {
        if (analysis.conditions() == null || analysis.conditions().paymentSchedules() == null) {
            return List.of();
        }
        List<PaymentScheduleView> views = new ArrayList<>();
        for (PaymentSchedule s : analysis.conditions().paymentSchedules()) {
            Long amount = DocumentFieldValues.amountOf(analysis, s.fieldKey()).orElse(null);
            views.add(new PaymentScheduleView(s.fieldKey(), s.fieldKey(), s.label(), amount, s.recurrence(),
                    s.dayOfMonth(), s.startDate(), s.endDate(), Boolean.TRUE.equals(s.userEdited())));
        }
        return views;
    }

    /** 요약 카드. 체크 항목의 done은 AI가 준 값이 아니라 BE에 저장된 값(사용자가 체크한 값)을 쓴다. */
    private SummaryCardView summaryCard(Long documentId, DocumentAnalysisResult result) {
        SummaryCard card = json.readSummaryCard(result.getSummaryCardJson());
        if (card == null) {
            return null;
        }
        Map<String, Boolean> done = checkItems.findByDocumentId(documentId).stream()
                .collect(Collectors.toMap(DocumentCheckItem::getCheckKey, DocumentCheckItem::isDone));
        List<Section> sections = card.sections() == null ? List.of() : card.sections().stream()
                .map(section -> "CHECKLIST".equals(section.type()) ? withSavedChecks(section, done) : section)
                .toList();
        return new SummaryCardView(card.oneLineSummary(), sections);
    }

    private Section withSavedChecks(Section section, Map<String, Boolean> done) {
        List<Map<String, Object>> items = section.items() == null ? List.of() : section.items().stream().map(item -> {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            Object key = item.get("checkKey");
            if (key != null && done.containsKey(String.valueOf(key))) {
                copy.put("done", done.get(String.valueOf(key)));
            }
            return copy;
        }).toList();
        return new Section(section.type(), section.title(), items);
    }

    /**
     * 대표 기한. AI priority.keyDeadline에 해당하는 기한이다.
     * 행동 기한(actionDate)이 있고 지나지 않았으면 그 기한, 아니면 대표 기한이다. 그런 기한이 없으면 가장 가까운 기한을 쓴다.
     */
    PrimaryDeadline pickPrimary(List<Deadline> documentDeadlines, Priority priority, Map<Long, Reminder> reminderByDeadline,
                                Set<String> completed) {
        LocalDate today = KstTime.today();
        KeyDeadline key = priority == null ? null : priority.keyDeadline();

        Optional<Deadline> picked = Optional.empty();
        if (key != null) {
            if (key.actionDate() != null && !key.actionDate().isBefore(today)) {
                picked = first(documentDeadlines, d -> d.getType() == DeadlineType.ACTION && Objects.equals(d.getFieldKey(), key.fieldKey()));
            }
            if (picked.isEmpty() && key.fieldKey() != null) {
                picked = first(documentDeadlines, d -> d.getType() != DeadlineType.ACTION && Objects.equals(d.getFieldKey(), key.fieldKey()));
            }
        }
        if (picked.isEmpty()) {
            picked = documentDeadlines.stream()
                    .filter(d -> DeadlineSchedule.nextOnOrAfter(d, today).isPresent())
                    .min(Comparator.comparing(d -> DeadlineSchedule.displayDate(d, today)));
        }
        return picked.map(d -> {
            LocalDate date = DeadlineSchedule.displayDate(d, today);
            return new PrimaryDeadline(d.getId(), d.getType(), d.getLabel(), date, KstTime.dDay(today, date),
                    reminderByDeadline.containsKey(d.getId()));
        }).orElse(null);
    }

    private Optional<Deadline> first(List<Deadline> list, Predicate<Deadline> filter) {
        return list.stream().filter(filter).findFirst();
    }
}
