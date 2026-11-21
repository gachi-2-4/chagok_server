package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.service.BoxService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.BoxRef;
import gachi.chagok_server.domain.document.dto.DocumentDtos.UploadResponse;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentPage;
import gachi.chagok_server.domain.document.entity.UploadSource;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentPageRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentFileProcessor;
import gachi.chagok_server.domain.document.support.DocumentFileProcessor.PageImage;
import gachi.chagok_server.domain.document.support.DocumentFileProcessor.Prepared;
import gachi.chagok_server.domain.document.support.DocumentFileProcessor.UploadedFile;
import gachi.chagok_server.domain.document.support.DocumentStorageKeys;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 서류 업로드. 파일을 검사해 저장소에 올리고 서류를 ANALYZING으로 만든 뒤 202를 주고,
 * AI 문서 분석은 커밋 뒤 비동기로 시작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    static final int MAX_TITLE_LENGTH = 50;
    static final String DEFAULT_TITLE = "새 서류";

    private final UserRepository users;
    private final BoxService boxService;
    private final DocumentRepository documents;
    private final DocumentPageRepository pages;
    private final DocumentFileProcessor processor;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;

    @Transactional
    public UploadResponse upload(Long userId, List<UploadedFile> files, Long boxId, String title, UploadSource source,
                                 String ocrResult) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Box box = boxService.find(userId, boxId);
        String customTitle = validTitle(title);

        // 파일 검사·가공이 모두 끝난 뒤에 서류를 만든다 (잘못된 파일로 빈 서류가 남지 않게).
        Prepared prepared = processor.prepare(files);

        Document document = documents.saveAndFlush(new Document(user, box,
                customTitle != null ? customTitle : defaultTitle(prepared.originalFileName()), customTitle != null,
                prepared.originalFileName(), prepared.pages().size(), source));

        String prefix = DocumentStorageKeys.prefix(userId, document.getId());
        try {
            store(user, document, prepared);
        } catch (RuntimeException e) {
            // DB는 롤백되므로 이미 올라간 파일을 정리한다.
            cleanUp(prefix);
            throw e;
        }

        events.publishEvent(new DocumentAnalysisRequestedEvent(document.getId(), userId, ocrResult, null));
        return new UploadResponse(document.getId(), document.getTitle(), document.getOriginalFileName(),
                new BoxRef(box.getId(), box.getName()), document.getStatus(), document.getAnalysisStep(),
                document.getPageCount(), KstTime.toOffset(document.getCreatedAt()));
    }

    private void store(User user, Document document, Prepared prepared) {
        Long userId = user.getId();
        Long documentId = document.getId();

        List<PageImage> images = prepared.pages();
        for (int i = 0; i < images.size(); i++) {
            PageImage image = images.get(i);
            String key = DocumentStorageKeys.page(userId, documentId, i + 1);
            storage.put(key, image.jpeg(), "image/jpeg");
            pages.save(new DocumentPage(document, i + 1, key, image.width(), image.height()));
        }
        String thumbnailKey = DocumentStorageKeys.thumbnail(userId, documentId);
        storage.put(thumbnailKey, prepared.thumbnail(), "image/jpeg");
        String pdfKey = DocumentStorageKeys.pdf(userId, documentId);
        storage.put(pdfKey, prepared.pdf(), "application/pdf");
        document.attachFiles(pdfKey, (long) prepared.pdf().length, thumbnailKey);
    }

    private void cleanUp(String prefix) {
        try {
            storage.deleteByPrefix(prefix);
        } catch (RuntimeException e) {
            log.warn("업로드 실패 뒤 파일을 정리하지 못했어요: prefix={}", prefix, e);
        }
    }

    /** 이름을 보냈다면 앞뒤 공백을 지운 뒤 1–50자여야 한다. 보내지 않았으면 null. */
    private String validTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String title = raw.trim();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_TITLE);
        }
        return title;
    }

    /** 분석 전 임시 이름: 올린 PDF 파일 이름(확장자 제외) 또는 "새 서류". 분석이 끝나면 AI가 준 이름으로 바뀐다. */
    static String defaultTitle(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return DEFAULT_TITLE;
        }
        int dot = originalFileName.lastIndexOf('.');
        String name = (dot > 0 ? originalFileName.substring(0, dot) : originalFileName).trim();
        if (name.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return name.length() > MAX_TITLE_LENGTH ? name.substring(0, MAX_TITLE_LENGTH) : name;
    }
}
