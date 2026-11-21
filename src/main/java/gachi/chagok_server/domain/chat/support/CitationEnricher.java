package gachi.chagok_server.domain.chat.support;

import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 답변의 근거(citations)는 그대로 두고, BE가 서류 이름(documentTitle)과 화면 표시 문구(label)만 붙인다.
 * 예) 계약서 1페이지, 민법 제398조
 */
@Component
@RequiredArgsConstructor
public class CitationEnricher {

    private final DocumentRepository documents;

    public List<Map<String, Object>> enrich(List<Map<String, Object>> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = citations.stream().map(c -> documentIdOf(c.get("documentId")))
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Document> byId = ids.isEmpty() ? Map.of() : documents.findAllById(ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> citation : citations) {
            Map<String, Object> copy = new LinkedHashMap<>(citation);
            if ("LAW".equals(citation.get("sourceType"))) {
                copy.put("label", lawLabel(citation));
            } else {
                Long documentId = documentIdOf(citation.get("documentId"));
                // AI의 문서 ID는 문자열일 수도 있어 숫자로 바꿔 준다.
                copy.put("documentId", documentId);
                Document document = documentId == null ? null : byId.get(documentId);
                copy.put("documentTitle", document == null ? null : document.getTitle());
                copy.put("label", documentLabel(document, citation.get("page")));
            }
            result.add(copy);
        }
        return result;
    }

    static Long documentIdOf(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String documentLabel(Document document, Object page) {
        String name = categoryName(document == null ? null : document.getDocCategory());
        return page instanceof Number n ? name + " " + n.intValue() + "페이지" : name;
    }

    private static String lawLabel(Map<String, Object> citation) {
        String law = citation.get("lawName") == null ? "" : String.valueOf(citation.get("lawName"));
        String article = citation.get("article") == null ? "" : String.valueOf(citation.get("article"));
        return (law + " " + article).trim();
    }

    private static String categoryName(DocCategory category) {
        if (category == null) {
            return "서류";
        }
        return switch (category) {
            case CONTRACT -> "계약서";
            case BILL -> "고지서";
            case AD -> "광고";
            case INFO -> "안내문";
        };
    }
}
