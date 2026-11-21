package gachi.chagok_server.domain.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.EvidenceLocation;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Party;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Priority;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Section;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.UserRelevance;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.DocumentDeadline;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.PrimaryDeadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.document.entity.AnalysisStep;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.FailReason;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 서류 API 요청·응답 DTO.
 */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record BoxRef(Long boxId, String name) {
    }

    // ---- 업로드 ----

    public record UploadResponse(Long documentId, String title, String originalFileName, BoxRef box,
                                 DocumentStatus status, AnalysisStep analysisStep, int pageCount, OffsetDateTime createdAt) {
    }

    // ---- 확인 ----

    public record ConfirmResponse(Long documentId, DocumentStatus status, PrimaryDeadline primaryDeadline) {
    }

    // ---- 목록 ----

    public enum Sort { RECENT, OLDEST, NAME, DEADLINE, PRIORITY }

    public record KeyCostView(String label, Long amount) {
    }

    public record ListPriority(String level, KeyCostView keyCost) {
    }

    public record NextDeadline(Long deadlineId, DeadlineType type, String label, LocalDate date, long dDay) {
    }

    public record ListItem(Long documentId, String title, DocType docType, DocCategory docCategory, BoxRef box,
                           DocumentStatus status, ListPriority priority, NextDeadline nextDeadline, String oneLineSummary,
                           int pageCount, String thumbnailUrl, OffsetDateTime createdAt) {
    }

    // ---- 상세 ----

    public record FieldView(String key, String label, String originalLabel, Object value, String valueType, String evidence,
                            EvidenceLocation evidenceLocation, Double confidence, boolean needsReview, boolean userEdited) {
    }

    public record PaymentScheduleView(String key, String fieldKey, String label, Long amount, String recurrence,
                                      Integer dayOfMonth, LocalDate startDate, LocalDate endDate, boolean userEdited) {
    }

    /**
     * 서류 상세. 분석 중이면 분석 결과 필드는 null이다.
     *
     * @param fullText OCR 원문. AI 서버(X-Internal-Key) 호출에만 채우고 앱 응답에는 필드 자체를 뺀다.
     */
    public record Detail(Long documentId, String title, String originalFileName, BoxRef box, DocumentStatus status,
                         AnalysisStep analysisStep, FailReason failReason, int pageCount, DocType docType,
                         DocCategory docCategory, List<String> classificationReasons, String summary, Boolean needsReview,
                         List<Party> parties, List<FieldView> fields, List<PaymentScheduleView> paymentSchedules,
                         Priority priority, PrimaryDeadline primaryDeadline, List<DocumentDeadline> deadlines,
                         SummaryCardView summaryCard, UserRelevance userRelevance, Map<String, Object> legalCheck,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String fullText) {

        public Detail withFullText(String text) {
            return new Detail(documentId, title, originalFileName, box, status, analysisStep, failReason, pageCount, docType,
                    docCategory, classificationReasons, summary, needsReview, parties, fields, paymentSchedules, priority,
                    primaryDeadline, deadlines, summaryCard, userRelevance, legalCheck, createdAt, updatedAt, text);
        }
    }

    public record SummaryCardView(String oneLineSummary, List<Section> sections) {
    }

    // ---- 원본·다운로드 ----

    public record PageView(int page, String imageUrl, int width, int height) {
    }

    public record OriginalResponse(Long documentId, int pageCount, List<PageView> pages, OffsetDateTime expiresAt) {
    }

    public record DownloadResponse(String fileName, String downloadUrl, long fileSize, int pageCount, OffsetDateTime expiresAt) {
    }

    // ---- 이름·박스 변경 ----

    public record RenameRequest(String title, Long boxId) {
    }

    public record RenameResponse(Long documentId, String title, BoxRef box, OffsetDateTime updatedAt) {
    }

    // ---- 내용 수정 ----

    public record FieldEdit(@NotNull String key, Object value) {
    }

    public record ScheduleEdit(@NotNull String key, Integer dayOfMonth) {
    }

    public record FieldsRequest(DocType docType, List<FieldEdit> fields, List<ScheduleEdit> paymentSchedules) {
    }

    // ---- 체크 항목 ----

    public record CheckRequest(@NotNull Boolean done) {
    }

    public record CheckResponse(String checkKey, String text, boolean done, OffsetDateTime updatedAt) {
    }
}
