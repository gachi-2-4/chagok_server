package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentCheckItemRepository;
import gachi.chagok_server.domain.document.repository.DocumentPageRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentStorageKeys;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

/**
 * 서류와 관련 데이터를 지운다. 되돌릴 수 없다.
 * DB에서는 바로 지우고, 저장소 파일은 커밋된 뒤 비동기로 지운다.
 * 서류를 쓴 비교 결과도 같이 지운다. 대화는 연결만 남기고 지우지 않는다(지난 대화 목록에 "삭제된 서류"로 보임).
 * (AI 서버 인덱스 삭제는 명세에 API가 없어 아직 호출하지 않는다.)
 */
@Service
@RequiredArgsConstructor
public class DocumentDeletionService {

    private final DocumentRepository documents;
    private final DocumentAnalysisResultRepository results;
    private final DocumentPageRepository pages;
    private final DocumentCheckItemRepository checkItems;
    private final DeadlineRepository deadlines;
    private final ReminderService reminderService;
    private final NotificationRepository notifications;
    private final ComparisonRepository comparisons;
    private final StorageCleaner storageCleaner;

    @Transactional
    public void delete(Long userId, Long documentId) {
        Document document = documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        deleteOne(document);
    }

    /** 박스 안의 서류를 모두 지운다. @return 지운 서류 수 (분석 실패 서류 포함) */
    @Transactional
    public int deleteAllInBox(Box box) {
        List<Document> inBox = documents.findByBoxId(box.getId());
        inBox.forEach(this::deleteOne);
        return inBox.size();
    }

    /**
     * 회원 탈퇴: 사용자의 서류를 모두 지운다 (서류별 파일·분석 결과·기한·알림·비교 결과 포함).
     * 저장소에는 서류별로 지우고도 남는 파일이 없도록 사용자 폴더 전체를 한 번 더 지운다.
     *
     * @return 지운 서류 수
     */
    @Transactional
    public int deleteAllOfUser(Long userId) {
        List<Document> mine = documents.findByUserId(userId);
        mine.forEach(this::deleteOne);
        runAfterCommit(() -> storageCleaner.deleteByPrefix(DocumentStorageKeys.userPrefix(userId)));
        return mine.size();
    }

    /** 분석에 실패한 지 cutoff보다 오래된 서류를 지운다 (24시간 뒤 자동 삭제). @return 지운 서류 수 */
    @Transactional
    public int deleteFailedBefore(Instant cutoff) {
        List<Document> expired = documents.findByStatusAndUpdatedAtBefore(DocumentStatus.FAILED, cutoff);
        expired.forEach(this::deleteOne);
        return expired.size();
    }

    private void deleteOne(Document document) {
        Long documentId = document.getId();
        String prefix = DocumentStorageKeys.prefix(document.getUser().getId(), documentId);

        // 이미 보낸 알림은 남기고, 연결된 서류·기한 정보만 비운다.
        notifications.detachDocument(documentId);
        // 이 서류로 비교한 결과도 같이 지운다. 그 비교 완료 알림은 남기되 비교 연결만 끊는다.
        List<Long> comparisonIds = comparisons.findIdsByDocument(documentId);
        if (!comparisonIds.isEmpty()) {
            notifications.detachComparisons(comparisonIds);
            comparisons.deleteByIds(comparisonIds);
        }
        checkItems.deleteByDocumentId(documentId);
        reminderService.deleteByDocument(documentId);
        deadlines.deleteByDocumentId(documentId);
        pages.deleteByDocumentId(documentId);
        results.findById(documentId).ifPresent(results::delete);
        documents.delete(document);
        documents.flush();

        runAfterCommit(() -> storageCleaner.deleteByPrefix(prefix));
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
