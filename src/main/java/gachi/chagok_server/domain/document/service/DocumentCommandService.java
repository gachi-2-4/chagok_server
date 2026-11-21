package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.service.BoxService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.BoxRef;
import gachi.chagok_server.domain.document.dto.DocumentDtos.CheckResponse;
import gachi.chagok_server.domain.document.dto.DocumentDtos.ConfirmResponse;
import gachi.chagok_server.domain.document.dto.DocumentDtos.RenameResponse;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentCheckItem;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentCheckItemRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentDetailAssembler;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서류 확인(보관), 이름·박스 변경, 체크 항목 변경, 삭제.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentCommandService {

    static final int MAX_TITLE_LENGTH = 50;

    private final DocumentRepository documents;
    private final DocumentAnalysisResultRepository results;
    private final DocumentCheckItemRepository checkItems;
    private final BoxService boxService;
    private final DocumentDetailAssembler assembler;
    private final DocumentDeletionService deletion;

    /** 읽어 낸 내용을 확인하고 보관한다. 이미 CONFIRMED면 그대로 돌려준다 (여러 번 눌러도 같은 결과). */
    @Transactional
    public ConfirmResponse confirm(Long userId, Long documentId) {
        Document document = find(userId, documentId);
        if (document.getStatus() == DocumentStatus.ANALYZING || document.getStatus() == DocumentStatus.FAILED) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        document.confirm();
        return new ConfirmResponse(documentId, document.getStatus(), assembler.primaryDeadline(document));
    }

    /** 서류 이름·박스만 바꾼다. 원본 파일과 분석 결과는 그대로다. */
    @Transactional
    public RenameResponse rename(Long userId, Long documentId, String title, Long boxId) {
        Document document = find(userId, documentId);
        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_TITLE);
            }
            document.rename(trimmed);
        }
        if (boxId != null) {
            Box box = boxService.find(userId, boxId);
            document.moveTo(box);
        }
        documents.flush();
        return new RenameResponse(documentId, document.getTitle(),
                new BoxRef(document.getBox().getId(), document.getBox().getName()), KstTime.toOffset(document.getUpdatedAt()));
    }

    /** AI 정리본의 '체크' 항목을 했음/안 했음으로 바꾼다. */
    @Transactional
    public CheckResponse changeCheck(Long userId, Long documentId, String checkKey, boolean done) {
        find(userId, documentId);
        DocumentAnalysisResult result = results.findById(documentId).orElse(null);
        if (result == null || result.getSummaryCardJson() == null) {
            throw new BusinessException(ErrorCode.SUMMARY_CARD_NOT_READY);
        }
        DocumentCheckItem item = checkItems.findByDocumentIdAndCheckKey(documentId, checkKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECK_ITEM_NOT_FOUND));
        item.markDone(done);
        checkItems.flush();
        return new CheckResponse(item.getCheckKey(), item.getText(), item.isDone(), KstTime.toOffset(item.getUpdatedAt()));
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        deletion.delete(userId, documentId);
    }

    private Document find(Long userId, Long documentId) {
        return documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }
}
