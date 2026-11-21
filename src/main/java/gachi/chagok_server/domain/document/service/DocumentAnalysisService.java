package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.FailReason;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 서류 AI 분석 (비동기). 업로드 트랜잭션이 커밋된 뒤 AI 문서 분석을 부르고, 결과를 서류에 반영한다.
 * 한 번의 AI 호출은 최대 90초 걸릴 수 있으므로 트랜잭션 밖에서 부르고, 단계마다 짧은 트랜잭션으로 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {

    /** 첫 호출을 포함한 최대 시도 횟수 (AI 500·시간 초과는 한 번 더 시도) */
    static final int MAX_ATTEMPTS = 2;

    private final DocumentRepository documents;
    private final FileStorage storage;
    private final AiServerClient ai;
    private final TransactionTemplate tx;
    private final DocumentResultApplier applier;
    private final SummaryCardService summaryCards;
    private final NotificationPublisher publisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(DocumentAnalysisRequestedEvent event) {
        analyze(event);
    }

    /** 테스트에서 직접 부를 수 있도록 공개한다. */
    public void analyze(DocumentAnalysisRequestedEvent event) {
        Source source = tx.execute(status -> startReading(event.documentId()));
        if (source == null) {
            return; // 서류가 지워졌거나 이미 처리됨
        }

        Analysis analysis;
        try {
            byte[] file = storage.get(source.pdfKey());
            analysis = callWithRetry(event, source, file);
        } catch (AiClientException e) {
            fail(event, e.getKind() == AiClientException.Kind.TEXT_NOT_RECOGNIZED
                    ? FailReason.TEXT_NOT_RECOGNIZED : FailReason.ANALYSIS_ERROR);
            return;
        } catch (RuntimeException e) {
            log.error("서류 파일을 읽지 못했어요: documentId={}", event.documentId(), e);
            fail(event, FailReason.ANALYSIS_ERROR);
            return;
        }

        try {
            tx.executeWithoutResult(status -> applier.apply(event.documentId(), analysis, event.restoreStatus()));
        } catch (RuntimeException e) {
            log.error("분석 결과를 저장하지 못했어요: documentId={}", event.documentId(), e);
            fail(event, FailReason.ANALYSIS_ERROR);
            return;
        }
        // 분석이 끝난 뒤 쉬운말 요약 카드를 이어서 만든다. 실패해도 분석 결과는 그대로다.
        summaryCards.generate(event.documentId(), event.userId());
    }

    private record Source(String pdfKey, String fileName) {
    }

    /** 분석 단계를 READING_TEXT로 바꾸고 AI에 보낼 파일 정보를 돌려준다. */
    private Source startReading(Long documentId) {
        Document document = documents.findById(documentId).orElse(null);
        if (document == null || document.getStatus() != DocumentStatus.ANALYZING) {
            return null;
        }
        document.changeStep(gachi.chagok_server.domain.document.entity.AnalysisStep.READING_TEXT);
        String name = document.getOriginalFileName() != null ? document.getOriginalFileName() : "document.pdf";
        return new Source(document.getPdfKey(), name);
    }

    private Analysis callWithRetry(DocumentAnalysisRequestedEvent event, Source source, byte[] file) {
        AiClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return ai.analyzeDocument(event.documentId(), event.userId(), file, source.fileName(), event.docTypeHint(),
                        event.ocrResult(), KstTime.today());
            } catch (AiClientException e) {
                last = e;
                if (!e.isRetryable()) {
                    break;
                }
                log.warn("AI 문서 분석 실패, 다시 시도해요: documentId={}, attempt={}, kind={}", event.documentId(), attempt, e.getKind());
            }
        }
        throw last;
    }

    private void fail(DocumentAnalysisRequestedEvent event, FailReason reason) {
        tx.executeWithoutResult(status -> {
            Document document = documents.findById(event.documentId()).orElse(null);
            if (document == null || document.getStatus() != DocumentStatus.ANALYZING) {
                return;
            }
            if (event.isRecovery()) {
                // 복구용 재분석이 실패해도 지금 분석 결과는 그대로 쓸 수 있으니 원래 상태로 돌려 놓는다 (FAILED면 24시간 뒤 지워진다).
                document.restoreStatus(event.restoreStatus());
                return;
            }
            document.fail(reason);
            String message = reason == FailReason.TEXT_NOT_RECOGNIZED
                    ? "글자를 읽지 못했어요. 밝은 곳에서 다시 촬영해 주세요."
                    : "분석 중에 문제가 생겼어요. 잠시 뒤에 다시 올려 주세요.";
            publisher.publish(document.getUser(), NotificationType.ANALYSIS_FAILED, "서류 분석에 실패했어요", message,
                    n -> n.withDocument(document.getId(), document.getTitle(), document.getBox().getName()));
        });
    }
}
