package gachi.chagok_server.domain.document.entity;

import jakarta.persistence.*;
import org.hibernate.Length;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 문서 분석 응답(사용자 수정 반영)·원문·요약 카드를 통째로 저장한다.
 * 크기가 커서 서류 목록 조회에는 읽히지 않도록 {@link Document}와 분리했다.
 * 큰 문자열 컬럼은 @Lob(CLOB) 대신 길이 LONG32(MySQL LONGTEXT)로 매핑한다. CLOB은 키워드 검색의 lower()·like를 쓸 수 없다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "document_analysis_results")
public class DocumentAnalysisResult {

    @Id
    private Long documentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    /** AI 문서 분석 응답 중 fullText를 뺀 나머지 (사용자 수정 반영) */
    @Column(length = Length.LONG32)
    private String analysisJson;

    /** OCR 원문 전체 ([N페이지] 표시). 앱에는 주지 않고 AI 서버에만 준다. */
    @Column(length = Length.LONG32)
    private String fullText;

    /** 키워드 검색용: 서류 이름·요약·항목 값을 이어 붙인 문자열 */
    @Column(length = Length.LONG32)
    private String searchText;

    /** AI 쉬운말 요약 카드 (아직 없으면 null) */
    @Column(length = Length.LONG32)
    private String summaryCardJson;

    public DocumentAnalysisResult(Document document) {
        this.document = document;
    }

    public void replace(String analysisJson, String fullText, String searchText) {
        this.analysisJson = analysisJson;
        this.fullText = fullText;
        this.searchText = searchText;
    }

    public void updateAnalysis(String analysisJson, String searchText) {
        this.analysisJson = analysisJson;
        this.searchText = searchText;
    }

    public void updateSummaryCard(String summaryCardJson) {
        this.summaryCardJson = summaryCardJson;
    }
}
