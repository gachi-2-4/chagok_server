package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.box.entity.Box;
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
 * (서류를 쓴 비교 결과·대화 연결·AI 인덱스 정리는 해당 기능이 생길 때 여기에 더한다.)
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
