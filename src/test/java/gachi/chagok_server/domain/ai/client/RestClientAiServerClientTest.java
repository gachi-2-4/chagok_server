package gachi.chagok_server.domain.ai.client;

import gachi.chagok_server.domain.ai.client.AiClientException.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientAiServerClientTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private AiClientException map(int status, String body) {
        JsonNode node = body == null ? null : mapper.readTree(body);
        return RestClientAiServerClient.toException(HttpStatusCode.valueOf(status), node);
    }

    @Test
    @DisplayName("422 NOT_COMPARABLE은 비교할 수 없는 이유와 함께 알린다")
    void notComparable() {
        AiClientException e = map(422, "{\"code\":\"NOT_COMPARABLE\",\"reason\":\"DIFFERENT_PROVIDER\"}");

        assertThat(e.getKind()).isEqualTo(Kind.NOT_COMPARABLE);
        assertThat(e.getReason()).isEqualTo("DIFFERENT_PROVIDER");
    }

    @Test
    @DisplayName("422 NO_DIFFERENCE는 차이가 없다는 뜻이다")
    void noDifference() {
        assertThat(map(422, "{\"code\":\"NO_DIFFERENCE\"}").getKind()).isEqualTo(Kind.NO_DIFFERENCE);
    }

    @Test
    @DisplayName("코드가 없는 422는 문서 분석의 글자 인식 실패로 본다")
    void legacyTextNotRecognized() {
        assertThat(map(422, "{\"code\":\"TEXT_NOT_RECOGNIZED\"}").getKind()).isEqualTo(Kind.TEXT_NOT_RECOGNIZED);
        assertThat(map(422, null).getKind()).isEqualTo(Kind.TEXT_NOT_RECOGNIZED);
    }

    @Test
    @DisplayName("404 INDEX_NOT_FOUND, 429, 5xx, 그 밖의 4xx를 구분한다")
    void otherStatuses() {
        assertThat(map(404, "{\"code\":\"INDEX_NOT_FOUND\"}").getKind()).isEqualTo(Kind.INDEX_NOT_FOUND);
        assertThat(map(404, "{\"code\":\"DOCUMENT_NOT_FOUND\"}").getKind()).isEqualTo(Kind.REJECTED);
        assertThat(map(429, "{\"code\":\"RATE_LIMITED\"}").getKind()).isEqualTo(Kind.RATE_LIMITED);
        assertThat(map(500, "{\"code\":\"AI_GENERATION_FAILED\"}").getKind()).isEqualTo(Kind.SERVER_ERROR);
        assertThat(map(502, null).isRetryable()).isTrue();
        assertThat(map(400, "{\"code\":\"INVALID_REQUEST\"}").isRetryable()).isFalse();
    }
}
