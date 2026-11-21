package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.CalendarEvent;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.KeyCost;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Priority;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
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
import gachi.chagok_server.domain.document.support.DocumentFieldValues;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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
        Document document = documents.findById(documentId).orElse(null);
        if (document == null || document.getStatus() != DocumentStatus.ANALYZING) {
            return;
        }

        // 원문(fullText)은 AI가 나중에 내부 API로 다시 가져가므로 따로 저장하고, 저장하는 JSON에서는 뺀다.
        Analysis stored = analysis.withoutFullText();
        String title = titleOf(analysis, document);
        DocumentAnalysisResult result = results.findById(documentId).orElseGet(() -> new DocumentAnalysisResult(document));
        result.replace(json.write(stored), analysis.fullText(), DocumentFieldValues.searchText(title, stored));
        results.save(result);

        Priority priority = analysis.priority();
        KeyCost keyCost = priority == null ? null : priority.keyCost();
        document.completeAnalysis(title, parse(DocType.class, analysis.docType(), DocType.ETC),
                parse(DocCategory.class, analysis.docCategory(), DocCategory.INFO), analysis.summary(),
                priority == null ? null : parse(PriorityLevel.class, priority.level(), PriorityLevel.LOW),
                priority == null ? null : priority.score(),
                keyCost == null ? null : keyCost.label(), keyCost == null ? null : keyCost.amount(),
                DocumentFieldValues.anyNeedsReview(analysis.fields()));

        replaceDeadlines(document, stored);

        publisher.publish(document.getUser(), NotificationType.ANALYSIS_COMPLETED, "서류 분석이 끝났어요",
                "'" + document.getTitle() + "' 분석이 끝났어요. 읽어 낸 내용을 확인해 주세요.",
                n -> n.withDocument(document.getId(), document.getTitle(), document.getBox().getName()));
    }

    /**
     * 분석 결과로 AI 기한을 새로 만든다. 다시 분석하는 경우 이전 AI 기한은 지우고 새로 만들되,
     * 사용자가 지운 AI 기한은 되살리지 않고 사용자가 끈 알림은 다시 켜지 않는다. 사용자가 추가한 기한은 그대로 둔다.
     */
    private void replaceDeadlines(Document document, Analysis analysis) {
        Set<String> removedByUser = new HashSet<>();
        Set<String> reminderOff = new HashSet<>();
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
            reminders.findByDeadlineId(old.getId()).ifPresent(reminders::delete);
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
                if (reminderOff.contains(key)) {
                    deadline.disableReminder();
                }
                deadlines.saveAndFlush(deadline);
                reminderService.autoCreate(deadline);
            });
        }
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
