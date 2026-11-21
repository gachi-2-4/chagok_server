package gachi.chagok_server.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 서류 비교·확인 요청 문자 응답. AI 응답 값 이름은 바꾸지 않고 앱에 그대로 넘기는 것이 원칙이라
 * 화면용 구조(highlight·comparisons·notices)는 풀지 않고 보관한다.
 */
public final class AiComparisonDtos {

    private AiComparisonDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Comparison(Long comparisonId, Long baseDocumentId, Long targetDocumentId, String compareType,
                             String status, String headline, Map<String, Object> highlight,
                             List<Map<String, Object>> comparisons, List<Map<String, Object>> notices, String disclaimer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InquiryMessage(Long comparisonId, Map<String, Object> message, String tone) {
    }
}
