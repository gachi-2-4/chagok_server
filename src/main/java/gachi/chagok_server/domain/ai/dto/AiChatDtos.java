package gachi.chagok_server.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 서류 기반 채팅·자주 묻는 질문 칩 응답.
 * 근거(citations)는 AI가 준 값을 그대로 저장하고 BE가 서류 이름·표시 문구만 붙이므로 구조를 풀지 않고 보관한다.
 */
public final class AiChatDtos {

    private AiChatDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatAnswer(String answer, String answerType, List<Map<String, Object>> citations, String disclaimer,
                             List<String> followUpQuestions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Suggestion(String text, String category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Suggestions(Long chatId, Long documentId, List<Suggestion> suggestions) {
    }
}
