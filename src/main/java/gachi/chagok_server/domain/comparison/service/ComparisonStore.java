package gachi.chagok_server.domain.comparison.service;

import gachi.chagok_server.domain.ai.dto.AiComparisonDtos;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.CompareRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.DocumentRef;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Response;
import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.Comparison;
import gachi.chagok_server.domain.comparison.entity.ComparisonStatus;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 결과의 저장·조회. AI 서버 호출은 시간이 걸리므로 트랜잭션 밖에서 하고(ComparisonService),
 * 이 클래스는 호출 앞뒤의 짧은 트랜잭션만 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComparisonStore {

    private final ComparisonRepository comparisons;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final NotificationPublisher publisher;
    private final DocumentJson json;

    /**
     * 비교할 수 있는 서류 쌍인지 검사하고, AI에 보낼 비교 ID를 먼저 만든다.
     * 기준 서류는 계약서, 비교 서류는 BILL이면 고지서·AD면 광고여야 하고, 둘 다 내 서류로 확인(CONFIRMED)까지 끝나 있어야 한다.
     */
    @Transactional
    public Long reserve(Long userId, CompareRequest request) {
        if (request.baseDocumentId().equals(request.targetDocumentId())) {
            throw new BusinessException(ErrorCode.SAME_DOCUMENT);
        }
        Document base = ownDocument(userId, request.baseDocumentId());
        Document target = ownDocument(userId, request.targetDocumentId());
        if (base.getStatus() != DocumentStatus.CONFIRMED || target.getStatus() != DocumentStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        DocCategory expectedTarget = request.compareType() == CompareType.BILL ? DocCategory.BILL : DocCategory.AD;
        if (base.getDocCategory() != DocCategory.CONTRACT || target.getDocCategory() != expectedTarget) {
            throw new BusinessException(ErrorCode.INVALID_COMPARE_PAIR);
        }
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        // 부를 때마다 새 비교 결과를 만든다.
        return comparisons.saveAndFlush(new Comparison(user, base.getId(), target.getId(), request.compareType())).getId();
    }

    /** AI 결과를 저장하고 완료 알림(읽음 상태, 푸시 없음)을 남긴다. */
    @Transactional
    public Response complete(Long userId, Long comparisonId, AiComparisonDtos.Comparison result) {
        Comparison comparison = comparisons.findByIdAndUserId(comparisonId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPARISON_NOT_FOUND));
        comparison.complete(statusOf(result.status()), json.write(result));

        Document base = documents.findById(comparison.getBaseDocumentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        publisher.publish(comparison.getUser(), NotificationType.COMPARISON_COMPLETED,
                base.getTitle() + " 비교가 완료됐어요", "비교 결과를 확인해 보세요.",
                n -> {
                    n.withDocument(base.getId(), base.getTitle(), base.getBox().getName());
                    n.withComparison(comparisonId);
                });
        return toResponse(comparison);
    }

    /** AI 호출이 실패하면 미리 만들어 둔 행을 지운다. */
    @Transactional
    public void discard(Long comparisonId) {
        comparisons.findById(comparisonId).filter(c -> !c.isCompleted()).ifPresent(comparisons::delete);
    }

    public Response get(Long userId, Long comparisonId) {
        return toResponse(find(userId, comparisonId));
    }

    /** 결과가 아직 없는(AI 호출 중인) 비교는 없는 것으로 본다. */
    public Comparison find(Long userId, Long comparisonId) {
        return comparisons.findByIdAndUserId(comparisonId, userId)
                .filter(Comparison::isCompleted)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPARISON_NOT_FOUND));
    }

    private Document ownDocument(Long userId, Long documentId) {
        return documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private Response toResponse(Comparison comparison) {
        AiComparisonDtos.Comparison stored = json.read(comparison.getResultJson(), AiComparisonDtos.Comparison.class);
        return new Response(comparison.getId(), comparison.getCompareType(), comparison.getStatus(), stored.headline(),
                documentRef(comparison.getBaseDocumentId()), documentRef(comparison.getTargetDocumentId()),
                stored.highlight(), stored.comparisons(), stored.notices(), stored.disclaimer(), comparison.isOutdated(),
                KstTime.toOffset(comparison.getCreatedAt()));
    }

    private DocumentRef documentRef(Long documentId) {
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPARISON_NOT_FOUND));
        return new DocumentRef(document.getId(), document.getTitle(), document.getDocType(), document.getBox().getName());
    }

    /** AI가 모르는 값을 주면 비교 항목이 불분명한 것으로 본다. */
    static ComparisonStatus statusOf(String raw) {
        try {
            return raw == null ? ComparisonStatus.NEEDS_CHECK : ComparisonStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ComparisonStatus.NEEDS_CHECK;
        }
    }
}
