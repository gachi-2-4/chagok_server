package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

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

    public <T> T read(String json, Class<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }

    /** 모양이 정해지지 않은 객체 목록 (AI가 준 근거 등)을 읽는다. 없으면 빈 목록. */
    public List<Map<String, Object>> readMaps(String json) {
        return json == null ? List.of() : mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    public List<String> readStrings(String json) {
        return json == null ? List.of() : mapper.readValue(json, new TypeReference<List<String>>() {
        });
    }
}
