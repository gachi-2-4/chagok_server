package gachi.chagok_server.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 서버 문서 분석·쉬운말 요약 카드 응답. 필드 이름은 AI 명세와 같게 쓴다
 * (AI 응답 값 이름은 바꾸지 않고 앱에 그대로 넘기는 것이 원칙).
 * 분석 응답은 사용자 수정을 덧붙여 통째로 JSON으로 저장하므로 저장·복원에도 이 타입을 쓴다.
 */
public final class AiDocumentDtos {

    private AiDocumentDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceLocation(Integer page, List<Double> bbox) {
    }

    /**
     * 추출된 항목 하나.
     *
     * @param aiValue    AI가 처음 읽은 값. 사용자가 고친 항목에만 채우며 앱에는 주지 않는다.
     * @param userEdited 사용자가 고친 값이면 true
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field(String key, String label, String originalLabel, Object value, String valueType, String evidence,
                        EvidenceLocation evidenceLocation, Double confidence, Boolean inferred, Boolean needsReview,
                        Boolean userEdited, Object aiValue) {

        public Field edited(Object newValue) {
            // 처음 고칠 때만 AI가 읽은 값을 보관한다.
            Object original = Boolean.TRUE.equals(userEdited) ? aiValue : value;
            return new Field(key, label, originalLabel, newValue, valueType, evidence, evidenceLocation, 1.0, inferred,
                    false, true, original);
        }

        public boolean isNeedsReview() {
            return Boolean.TRUE.equals(needsReview);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Party(String role, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentSchedule(String fieldKey, String label, String recurrence, Integer dayOfMonth,
                                  LocalDate startDate, LocalDate endDate, Boolean userEdited) {

        public PaymentSchedule withDayOfMonth(int day) {
            return new PaymentSchedule(fieldKey, label, recurrence, day, startDate, endDate, true);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conditions(List<PaymentSchedule> paymentSchedules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalendarEvent(String type, String fieldKey, String title, LocalDate date, String rrule,
                                List<Integer> remindBeforeDays) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRelevance(String level, List<String> reasons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeyCost(String fieldKey, String label, Long amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeyDeadline(String fieldKey, String label, LocalDate date, String actionLabel, LocalDate actionDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Priority(String level, Integer score, List<String> reasons, KeyCost keyCost, KeyDeadline keyDeadline) {
    }

    /**
     * AI 문서 분석 응답. legalCheck는 앱에 그대로 넘기므로 구조를 풀지 않고 보관한다.
     * fullText는 별도 컬럼에 저장하기 때문에 저장하는 JSON에서는 null로 비운다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Analysis(Long documentId, String docType, Double docTypeConfidence, String docCategory,
                           List<String> classificationReasons, String title, String summary, List<Party> parties,
                           List<Field> fields, Conditions conditions, List<CalendarEvent> calendarEvents,
                           Boolean needsReview, UserRelevance userRelevance, String fullText, Integer pageCount,
                           Map<String, Object> legalCheck, Priority priority, List<String> warnings) {

        public Analysis withFields(List<Field> newFields, Conditions newConditions) {
            return new Analysis(documentId, docType, docTypeConfidence, docCategory, classificationReasons, title, summary,
                    parties, newFields, newConditions, calendarEvents, newFields.stream().anyMatch(Field::isNeedsReview),
                    userRelevance, fullText, pageCount, legalCheck, priority, warnings);
        }

        public Analysis withoutFullText() {
            return new Analysis(documentId, docType, docTypeConfidence, docCategory, classificationReasons, title, summary,
                    parties, fields, conditions, calendarEvents, needsReview, userRelevance, null, pageCount, legalCheck,
                    priority, warnings);
        }
    }

    /** 요약 카드 섹션. items의 모양이 type마다 달라(KEY_VALUE / BULLET / CHECKLIST) AI가 준 그대로 보관한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String type, String title, List<Map<String, Object>> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummaryCard(Long documentId, String category, String title, String oneLineSummary, List<Section> sections) {
    }
}
