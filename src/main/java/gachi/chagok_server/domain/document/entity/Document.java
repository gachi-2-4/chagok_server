package gachi.chagok_server.domain.document.entity;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 올린 서류. 큰 분석 결과(JSON·원문)는 {@link DocumentAnalysisResult}에 따로 둔다.
 * 목록 조회·정렬·검색에 쓰는 값만 이 엔티티에 컬럼으로 복사해 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_user_status", columnList = "user_id,status"),
        @Index(name = "idx_documents_box", columnList = "box_id")
})
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id")
    private Box box;

    @Column(nullable = false, length = 100)
    private String title;

    /** 올린 PDF 파일 이름 (사진이면 null) */
    @Column(length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStep analysisStep;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private FailReason failReason;

    @Column(nullable = false)
    private int pageCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UploadSource source;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DocType docType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DocCategory docCategory;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PriorityLevel priorityLevel;

    /** 정렬용 (HIGH=3 … LOW=1). 분석 전에는 0 */
    @Column(nullable = false)
    private int priorityRank;

    private Integer priorityScore;

    /** AI priority.keyCost가 있는 서류인지 (hasCost 필터) */
    @Column(nullable = false)
    private boolean hasCost;

    /** 대표 비용 (서류 목록 카드에 보여 줌) */
    @Column(length = 60)
    private String keyCostLabel;

    private Long keyCostAmount;

    @Column(length = 1000)
    private String summary;

    /** 쉬운말 요약 카드의 한 줄 요약 (아직 없으면 null) */
    @Column(length = 500)
    private String oneLineSummary;

    @Column(nullable = false)
    private boolean needsReview;

    /** 사용자가 이름을 직접 정했으면 true. 분석이 끝나도 AI 제목으로 바꾸지 않는다. */
    @Column(nullable = false)
    private boolean titleCustom;

    /** 원본(또는 합친) PDF 저장 키 */
    @Column(length = 300)
    private String pdfKey;

    private Long pdfSize;

    @Column(length = 300)
    private String thumbnailKey;

    public Document(User user, Box box, String title, boolean titleCustom, String originalFileName, int pageCount, UploadSource source) {
        this.user = user;
        this.box = box;
        this.title = title;
        this.titleCustom = titleCustom;
        this.originalFileName = originalFileName;
        this.pageCount = pageCount;
        this.source = source;
        this.status = DocumentStatus.ANALYZING;
        this.analysisStep = AnalysisStep.ENHANCING;
    }

    public void attachFiles(String pdfKey, Long pdfSize, String thumbnailKey) {
        this.pdfKey = pdfKey;
        this.pdfSize = pdfSize;
        this.thumbnailKey = thumbnailKey;
    }

    public void changeStep(AnalysisStep step) {
        this.analysisStep = step;
    }

    /** 분석이 끝나 확인을 기다리는 상태로 만든다. */
    public void completeAnalysis(String title, DocType docType, DocCategory docCategory, String summary,
                                 PriorityLevel level, Integer score, String keyCostLabel, Long keyCostAmount,
                                 boolean needsReview) {
        if (!titleCustom) {
            this.title = title;
        }
        this.docType = docType;
        this.docCategory = docCategory;
        this.summary = summary;
        this.priorityLevel = level;
        this.priorityRank = level == null ? 0 : level.getRank();
        this.priorityScore = score;
        this.keyCostLabel = keyCostLabel;
        this.keyCostAmount = keyCostAmount;
        this.hasCost = keyCostLabel != null || keyCostAmount != null;
        this.needsReview = needsReview;
        this.status = DocumentStatus.REVIEW_REQUIRED;
        this.analysisStep = AnalysisStep.DONE;
        this.failReason = null;
    }

    public void fail(FailReason reason) {
        this.status = DocumentStatus.FAILED;
        this.analysisStep = AnalysisStep.DONE;
        this.failReason = reason;
    }

    /** 서류 종류 변경 등으로 다시 분석을 시작한다. */
    public void restartAnalysis() {
        this.status = DocumentStatus.ANALYZING;
        this.analysisStep = AnalysisStep.READING_TEXT;
        this.failReason = null;
    }

    public void confirm() {
        this.status = DocumentStatus.CONFIRMED;
    }

    public void rename(String title) {
        this.title = title;
        this.titleCustom = true;
    }

    public void moveTo(Box box) {
        this.box = box;
    }

    public void updateOneLineSummary(String oneLineSummary) {
        this.oneLineSummary = oneLineSummary;
    }

    public void updateNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    /** 분석이 끝난 서류인지 (기한 추가·서류 내용 수정·AI 호출이 가능한 상태) */
    public boolean isAnalyzed() {
        return status == DocumentStatus.REVIEW_REQUIRED || status == DocumentStatus.CONFIRMED;
    }
}
