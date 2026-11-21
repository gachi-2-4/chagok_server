package gachi.chagok_server.domain.chat.support;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CitationEnricherTest {

    @Mock DocumentRepository documents;
    @InjectMocks CitationEnricher enricher;

    private Document contract() {
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        Document document = new Document(user, new Box(user, "집", BoxColor.PEACH, BoxIcon.HOME), "임대차계약서", false, null, 1, null);
        document.completeAnalysis("임대차계약서", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, "요약", PriorityLevel.HIGH, 80, null, null, false);
        ReflectionTestUtils.setField(document, "id", 501L);
        return document;
    }

    @Test
    @DisplayName("서류 근거에는 서류 이름과 '계약서 1페이지' 같은 표시 문구를 붙이고 AI가 준 값은 그대로 둔다")
    void enrichesDocumentCitation() {
        // given
        given(documents.findAllById(any())).willReturn(List.of(contract()));
        Map<String, Object> citation = Map.of("sourceType", "DOCUMENT", "documentId", 501, "page", 1,
                "snippet", "차임은 매월 25일에 지급하기로 한다.", "score", 0.82);

        // when
        List<Map<String, Object>> result = enricher.enrich(List.of(citation));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("documentTitle", "임대차계약서").containsEntry("label", "계약서 1페이지")
                .containsEntry("documentId", 501L).containsEntry("snippet", "차임은 매월 25일에 지급하기로 한다.")
                .containsEntry("score", 0.82);
    }

    @Test
    @DisplayName("AI가 문서 ID를 문자열로 줘도 숫자로 바꿔 준다")
    void convertsStringDocumentId() {
        // given
        given(documents.findAllById(any())).willReturn(List.of(contract()));

        // when
        List<Map<String, Object>> result = enricher.enrich(List.of(Map.of("sourceType", "DOCUMENT", "documentId", "501", "page", 2)));

        // then
        assertThat(result.get(0)).containsEntry("documentId", 501L).containsEntry("label", "계약서 2페이지");
    }

    @Test
    @DisplayName("법 조문 근거는 법 이름과 조문으로 표시 문구를 만든다")
    void lawCitation() {
        // when
        List<Map<String, Object>> result = enricher.enrich(List.of(
                Map.of("sourceType", "LAW", "lawName", "민법", "article", "제398조", "url", "https://www.law.go.kr/")));

        // then
        assertThat(result.get(0)).containsEntry("label", "민법 제398조").containsEntry("url", "https://www.law.go.kr/");
        verifyNoInteractions(documents);
    }

    @Test
    @DisplayName("그 사이 지워진 서류의 근거는 서류 이름 없이 준다")
    void deletedDocument() {
        // given
        given(documents.findAllById(any())).willReturn(List.of());

        // when
        List<Map<String, Object>> result = enricher.enrich(List.of(Map.of("sourceType", "DOCUMENT", "documentId", 999, "page", 1)));

        // then
        assertThat(result.get(0).get("documentTitle")).isNull();
        assertThat(result.get(0)).containsEntry("label", "서류 1페이지");
    }

    @Test
    @DisplayName("근거가 없으면 빈 목록")
    void empty() {
        assertThat(enricher.enrich(null)).isEmpty();
        assertThat(enricher.enrich(List.of())).isEmpty();
    }
}
