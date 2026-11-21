package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Section;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentCheckItem;
import gachi.chagok_server.domain.document.event.SummaryCardRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentCheckItemRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 쉬운말 요약 카드를 만들어 저장한다. 요약 카드는 분석이 끝난 뒤, 서류 내용을 고친 뒤에 (다시) 만든다.
 * 실패해도 서류는 그대로 쓸 수 있다 (카드가 아직 없으면 상세에서는 summary를 보여 준다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryCardService {

    private final AiServerClient ai;
    private final DocumentRepository documents;
    private final DocumentAnalysisResultRepository results;
    private final DocumentCheckItemRepository checkItems;
    private final DocumentJson json;
    private final TransactionTemplate tx;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(SummaryCardRequestedEvent event) {
        generate(event.documentId(), event.userId());
    }

    /** AI 호출은 트랜잭션 밖에서 하고, 받은 카드만 짧은 트랜잭션으로 저장한다. */
    public void generate(Long documentId, Long userId) {
        SummaryCard card;
        try {
            card = ai.summaryCard(documentId, userId);
        } catch (AiClientException e) {
            log.warn("쉬운말 요약 카드를 만들지 못했어요: documentId={}, kind={}", documentId, e.getKind());
            return;
        }
        try {
            tx.executeWithoutResult(status -> save(documentId, card));
        } catch (RuntimeException e) {
            log.error("쉬운말 요약 카드를 저장하지 못했어요: documentId={}", documentId, e);
        }
    }

    private void save(Long documentId, SummaryCard card) {
        Document document = documents.findById(documentId).orElse(null);
        DocumentAnalysisResult result = results.findById(documentId).orElse(null);
        if (document == null || result == null) {
            return; // 그 사이 서류가 지워짐
        }
        result.updateSummaryCard(json.write(card));
        document.updateOneLineSummary(card.oneLineSummary());
        syncCheckItems(document, card);
    }

    /**
     * 체크 항목 상태를 맞춘다. AI가 주는 done은 처음 만들 때만 쓰고, 이미 있는 checkKey는 사용자가 체크한 값을 유지한다.
     */
    private void syncCheckItems(Document document, SummaryCard card) {
        Map<String, DocumentCheckItem> saved = checkItems.findByDocumentId(document.getId()).stream()
                .collect(Collectors.toMap(DocumentCheckItem::getCheckKey, c -> c));
        Set<String> present = new HashSet<>();

        List<Section> sections = card.sections() == null ? List.of() : card.sections();
        for (Section section : sections) {
            if (!"CHECKLIST".equals(section.type()) || section.items() == null) {
                continue;
            }
            for (Map<String, Object> item : section.items()) {
                Object key = item.get("checkKey");
                if (key == null) {
                    continue;
                }
                String checkKey = String.valueOf(key);
                String text = String.valueOf(item.getOrDefault("text", ""));
                present.add(checkKey);
                DocumentCheckItem existing = saved.get(checkKey);
                if (existing != null) {
                    existing.changeText(text);
                } else {
                    checkItems.save(new DocumentCheckItem(document, checkKey, text, Boolean.TRUE.equals(item.get("done"))));
                }
            }
        }
        // 새 카드에 없는 체크 항목은 지운다.
        saved.values().stream().filter(c -> !present.contains(c.getCheckKey())).forEach(checkItems::delete);
    }
}
