package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.deadline.schedule.DeadlineOccurrence;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.domain.deadline.service.DeadlineQueryService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.*;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentPage;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentPageRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentDetailAssembler;
import gachi.chagok_server.domain.document.support.DocumentFieldValues;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.storage.FileStorage;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 서류 목록·상세·원본·PDF 다운로드 조회.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentQueryService {

    static final int MAX_PAGE_SIZE = 50;
    /** Presigned URL 유효 시간 */
    static final Duration URL_VALID_FOR = Duration.ofMinutes(10);

    private final DocumentRepository documents;
    private final DocumentAnalysisResultRepository results;
    private final DocumentPageRepository pages;
    private final DeadlineQueryService deadlineQuery;
    private final DocumentDetailAssembler assembler;
    private final FileStorage storage;

    /** 서류 목록 조회 조건 */
    public record Filter(Long boxId, String keyword, PriorityLevel priority, Integer dueWithinDays, Boolean hasCost,
                         List<DocCategory> docCategories, DocType docType) {
    }

    public PageResponse<ListItem> list(Long userId, Filter filter, Sort sort, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || (filter.dueWithinDays() != null && filter.dueWithinDays() < 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Specification<Document> spec = specification(userId, filter);
        DeadlineSnapshot deadlines = deadlineQuery.snapshot(userId);

        boolean needsDeadlines = sort == Sort.DEADLINE || filter.dueWithinDays() != null;
        if (!needsDeadlines) {
            Page<Document> found = documents.findAll(spec, PageRequest.of(page, size, dbSort(sort)));
            return PageResponse.of(found, d -> toItem(d, deadlines));
        }

        // 기한은 회차를 계산해야 해서 DB에서 정렬·필터링하지 못한다. 한 사용자의 서류는 많지 않으므로 메모리에서 처리한다.
        List<Document> all = documents.findAll(spec, org.springframework.data.domain.Sort.by(Direction.DESC, "createdAt", "id"));
        List<ListItem> items = new ArrayList<>(all.stream().map(d -> toItem(d, deadlines)).toList());
        if (filter.dueWithinDays() != null) {
            var limit = KstTime.today().plusDays(filter.dueWithinDays());
            items.removeIf(i -> i.nextDeadline() == null || i.nextDeadline().date().isAfter(limit));
        }
        if (sort == Sort.DEADLINE) {
            // 기한이 가까운 순. 기한이 없는 서류는 뒤로 보낸다.
            items.sort(Comparator.comparing((ListItem i) -> i.nextDeadline() == null ? null : i.nextDeadline().date(),
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return PageResponse.ofList(items, page, size);
    }

    public Detail detail(Long userId, Long documentId) {
        return assembler.assemble(find(userId, documentId));
    }

    /**
     * AI 서버(X-Internal-Key) 호출용 상세. 앱 응답과 달리 OCR 원문(fullText)을 담는다.
     * 분석 중이거나 실패한 서류는 409다.
     */
    public Detail internalDetail(Long userId, Long documentId) {
        Document document = find(userId, documentId);
        if (!document.isAnalyzed()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        String fullText = results.findById(documentId).map(DocumentAnalysisResult::getFullText).orElse(null);
        return assembler.assemble(document).withFullText(fullText == null ? "" : fullText);
    }

    public OriginalResponse original(Long userId, Long documentId, Integer page) {
        Document document = find(userId, documentId);
        if (page != null && (page < 1 || page > document.getPageCount())) {
            throw new BusinessException(ErrorCode.INVALID_PAGE);
        }
        List<PageView> views = pages.findByDocumentIdOrderByPageNoAsc(documentId).stream()
                .filter(p -> page == null || p.getPageNo() == page)
                .map(this::toPageView)
                .toList();
        return new OriginalResponse(documentId, document.getPageCount(), views,
                KstTime.toOffset(Instant.now().plus(URL_VALID_FOR)));
    }

    public DownloadResponse download(Long userId, Long documentId, String type) {
        if (type != null && !"ORIGINAL".equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Document document = find(userId, documentId);
        if (document.getPdfKey() == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        String fileName = fileNameOf(document.getTitle());
        String url = storage.presignedGetUrl(document.getPdfKey(), URL_VALID_FOR, fileName);
        return new DownloadResponse(fileName, url, document.getPdfSize() == null ? 0 : document.getPdfSize(),
                document.getPageCount(), KstTime.toOffset(Instant.now().plus(URL_VALID_FOR)));
    }

    /** 내 서류 조회. 없는 서류와 다른 사용자의 서류는 구분하지 않고 404다 (존재 여부를 숨김). */
    Document find(Long userId, Long documentId) {
        return documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /** 파일 이름에 쓸 수 없는 문자는 _로 바꾼다. */
    static String fileNameOf(String title) {
        String safe = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return (safe.isEmpty() ? "서류" : safe) + ".pdf";
    }

    private PageView toPageView(DocumentPage page) {
        return new PageView(page.getPageNo(), storage.presignedGetUrl(page.getImageKey(), URL_VALID_FOR, null),
                page.getWidth(), page.getHeight());
    }

    private ListItem toItem(Document d, DeadlineSnapshot deadlines) {
        ListPriority priority = d.getPriorityLevel() == null ? null : new ListPriority(d.getPriorityLevel().name(),
                d.isHasCost() ? new KeyCostView(d.getKeyCostLabel(), d.getKeyCostAmount()) : null);
        NextDeadline next = deadlines.nextOfDocument(d.getId()).map(o -> toNext(o, deadlines)).orElse(null);
        String thumbnailUrl = d.getThumbnailKey() == null ? null : storage.presignedGetUrl(d.getThumbnailKey(), URL_VALID_FOR, null);
        // 요약 카드가 아직 없으면 AI 문서 분석의 summary를 보여 준다.
        String summary = d.getOneLineSummary() != null ? d.getOneLineSummary() : d.getSummary();
        return new ListItem(d.getId(), d.getTitle(), d.getDocType(), d.getDocCategory(),
                new BoxRef(d.getBox().getId(), d.getBox().getName()), d.getStatus(), priority, next, summary,
                d.getPageCount(), thumbnailUrl, KstTime.toOffset(d.getCreatedAt()));
    }

    private NextDeadline toNext(DeadlineOccurrence o, DeadlineSnapshot deadlines) {
        return new NextDeadline(o.deadline().getId(), o.deadline().getType(), o.deadline().getLabel(), o.date(),
                KstTime.dDay(deadlines.today(), o.date()));
    }

    private org.springframework.data.domain.Sort dbSort(Sort sort) {
        return switch (sort) {
            case OLDEST -> org.springframework.data.domain.Sort.by(Direction.ASC, "createdAt", "id");
            case NAME -> org.springframework.data.domain.Sort.by(Direction.ASC, "title").and(org.springframework.data.domain.Sort.by(Direction.DESC, "id"));
            // 중요도 등급 → AI priority.score 순
            case PRIORITY -> org.springframework.data.domain.Sort.by(Direction.DESC, "priorityRank", "priorityScore", "id");
            default -> org.springframework.data.domain.Sort.by(Direction.DESC, "createdAt", "id");
        };
    }

    /** 내 서류 중 분석에 실패하지 않은 것에 조건을 건다. */
    private Specification<Document> specification(Long userId, Filter f) {
        return (root, query, cb) -> {
            // 서류 목록은 박스 이름을 같이 보여 주므로 함께 읽는다 (개수 조회에서는 제외).
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("box");
            }
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("user").get("id"), userId));
            where.add(cb.notEqual(root.get("status"), DocumentStatus.FAILED));
            if (f.boxId() != null) {
                where.add(cb.equal(root.get("box").get("id"), f.boxId()));
            }
            if (f.priority() != null) {
                where.add(cb.equal(root.get("priorityLevel"), f.priority()));
            }
            if (f.hasCost() != null) {
                where.add(cb.equal(root.get("hasCost"), f.hasCost()));
            }
            if (f.docCategories() != null && !f.docCategories().isEmpty()) {
                where.add(root.get("docCategory").in(f.docCategories()));
            }
            if (f.docType() != null) {
                where.add(cb.equal(root.get("docType"), f.docType()));
            }
            if (f.keyword() != null && !f.keyword().isBlank()) {
                where.add(keywordPredicate(root, query, cb, f.keyword().trim()));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /** 서류 이름, 요약, 읽어 낸 항목 값, 원문에서 찾는다. 금액으로 읽히는 검색어(50만, 500,000)는 금액 항목과도 맞춰 본다. */
    private Predicate keywordPredicate(Root<Document> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                       jakarta.persistence.criteria.CriteriaBuilder cb, String keyword) {
        List<String> patterns = new ArrayList<>();
        patterns.add(likePattern(keyword));
        Optional<Long> amount = DocumentFieldValues.parseAmountKeyword(keyword);
        amount.ifPresent(value -> patterns.add(likePattern(String.valueOf(value))));

        List<Predicate> any = new ArrayList<>();
        for (String pattern : patterns) {
            any.add(cb.like(cb.lower(root.get("title")), pattern, '\\'));
            any.add(cb.like(cb.lower(root.get("summary")), pattern, '\\'));
            any.add(cb.like(cb.lower(root.get("oneLineSummary")), pattern, '\\'));
        }

        Subquery<Long> sub = query.subquery(Long.class);
        Root<DocumentAnalysisResult> result = sub.from(DocumentAnalysisResult.class);
        // searchText는 소문자로 저장해 두므로 lower() 없이 비교한다. (큰 문자열 컬럼은 CLOB으로 매핑돼 lower()를 쓸 수 없다.)
        // 원문(fullText)은 DB 문자열 비교 규칙(MySQL 기본 collation은 대소문자 구분 없음)을 따른다.
        List<Predicate> inResult = new ArrayList<>();
        for (String pattern : patterns) {
            inResult.add(cb.like(result.<String>get("searchText"), pattern, '\\'));
        }
        inResult.add(cb.like(result.<String>get("fullText"), patterns.get(0), '\\'));
        sub.select(result.get("documentId"))
                .where(cb.equal(result.get("documentId"), root.get("id")), cb.or(inResult.toArray(new Predicate[0])));
        any.add(cb.exists(sub));
        return cb.or(any.toArray(new Predicate[0]));
    }

    /** LIKE의 특수문자(% _ \)를 문자 그대로 찾도록 막고 소문자로 맞춘다. */
    static String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
