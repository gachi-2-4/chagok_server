package gachi.chagok_server.domain.comparison.dto;

import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.ComparisonStatus;
import gachi.chagok_server.domain.document.entity.DocType;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 서류 비교 API 요청·응답 DTO.
 */
public final class ComparisonDtos {

    private ComparisonDtos() {
    }

    public enum Tone { POLITE, FRIENDLY, FORMAL }

    public record CompareRequest(@NotNull Long baseDocumentId, @NotNull Long targetDocumentId, @NotNull CompareType compareType) {
    }

    public record InquiryRequest(Tone tone, String extraRequest) {
    }

    public record DocumentRef(Long documentId, String title, DocType docType, String boxName) {
    }

    /**
     * 비교 결과. status·headline·highlight·comparisons·notices·disclaimer는 AI 응답 그대로이고,
     * 비교 ID·서류 정보·outdated·createdAt은 BE가 붙인다.
     */
    public record Response(Long comparisonId, CompareType compareType, ComparisonStatus status, String headline,
                           DocumentRef baseDocument, DocumentRef targetDocument, Map<String, Object> highlight,
                           List<Map<String, Object>> comparisons, List<Map<String, Object>> notices, String disclaimer,
                           boolean outdated, OffsetDateTime createdAt) {
    }
}
