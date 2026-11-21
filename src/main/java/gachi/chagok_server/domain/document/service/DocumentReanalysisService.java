package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * AI 서버가 서류를 잃어버렸을 때(INDEX_NOT_FOUND) 서류 분석을 다시 요청한다.
 * 서류 종류를 바꿔 다시 분석할 때와 달리 사용자가 고친 값은 새 결과에 이어 붙이고,
 * 끝나면 원래 상태(확인 완료 등)로 되돌린다. 실패해도 서류를 실패 처리하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentReanalysisService {

    private final DocumentRepository documents;
    private final ApplicationEventPublisher events;

    /** 이미 분석 중이거나 분석이 끝나지 않은 서류는 건너뛴다. */
    @Transactional
    public void requestRecovery(Long userId, Collection<Long> documentIds) {
        for (Long documentId : documentIds) {
            Document document = documents.findByIdAndUserId(documentId, userId).orElse(null);
            if (document == null || !document.isAnalyzed()) {
                continue;
            }
            var previous = document.getStatus();
            // 사용자가 정한(AI가 판별한) 서류 종류를 힌트로 줘서 같은 종류로 다시 분석한다.
            String hint = document.getDocType() == null ? null : document.getDocType().name();
            document.restartAnalysis();
            events.publishEvent(new DocumentAnalysisRequestedEvent(documentId, userId, null, hint, previous));
        }
    }
}
