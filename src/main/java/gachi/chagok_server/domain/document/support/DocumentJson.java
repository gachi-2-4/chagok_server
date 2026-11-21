package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 분석 결과·요약 카드를 DB에 JSON 문자열로 저장하고 되돌린다.
 */
@Component
@RequiredArgsConstructor
public class DocumentJson {

    private final JsonMapper mapper;

    public String write(Object value) {
        return value == null ? null : mapper.writeValueAsString(value);
    }

    public Analysis readAnalysis(String json) {
        return json == null ? null : mapper.readValue(json, Analysis.class);
    }

    public SummaryCard readSummaryCard(String json) {
        return json == null ? null : mapper.readValue(json, SummaryCard.class);
    }
}
