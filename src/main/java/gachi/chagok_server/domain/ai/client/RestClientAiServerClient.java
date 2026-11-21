package gachi.chagok_server.domain.ai.client;

import gachi.chagok_server.domain.ai.client.AiClientException.Kind;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * Spring RestClient로 AI 서버를 부르는 구현.
 * 경로는 AI 명세 페이지 기준이다 (서류 업로드 설명의 /api/ai/documents/analyze와 다름 — 팀 확정 필요).
 */
@Component
public class RestClientAiServerClient implements AiServerClient {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final RestClient analysisClient;
    private final RestClient summaryClient;
    private final String apiKey;

    public RestClientAiServerClient(@Value("${chagok.ai.base-url}") String baseUrl,
                                    @Value("${chagok.ai.api-key}") String apiKey,
                                    @Value("${chagok.ai.analysis-timeout-seconds:90}") int analysisTimeoutSeconds,
                                    @Value("${chagok.ai.summary-timeout-seconds:15}") int summaryTimeoutSeconds) {
        this.apiKey = apiKey;
        this.analysisClient = build(baseUrl, Duration.ofSeconds(analysisTimeoutSeconds));
        this.summaryClient = build(baseUrl, Duration.ofSeconds(summaryTimeoutSeconds));
    }

    private static RestClient build(String baseUrl, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Analysis analyzeDocument(Long documentId, Long userId, byte[] file, String fileName, String docTypeHint,
                                    String ocrResult, LocalDate baseDate) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("userId", String.valueOf(userId));
        if (docTypeHint != null) {
            body.add("docTypeHint", docTypeHint);
        }
        if (ocrResult != null) {
            body.add("ocrResult", ocrResult);
        }

        return call(() -> analysisClient.post()
                .uri(uri -> uri.path("/ai/documents/{id}/analysis")
                        .queryParam("baseDate", baseDate)
                        .queryParam("lang", "ko")
                        .build(documentId))
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw toException(response.getStatusCode());
                })
                .body(Analysis.class));
    }

    @Override
    public SummaryCard summaryCard(Long documentId, Long userId) {
        // 명세는 GET + JSON Body라 서버·클라이언트에 따라 Body가 무시될 수 있다. POST로 보낸다 (AI 팀과 확정 필요).
        return call(() -> summaryClient.post()
                .uri("/ai/documents/summary-card")
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("documentId", documentId, "userId", userId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw toException(response.getStatusCode());
                })
                .body(SummaryCard.class));
    }

    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new AiClientException(Kind.SERVER_ERROR, "AI 서버가 빈 응답을 줬어요.");
            }
            return result;
        } catch (AiClientException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // 연결 실패·읽기 시간 초과는 모두 한 번 더 시도할 수 있는 오류로 본다.
            boolean timeout = e.getCause() instanceof java.net.http.HttpTimeoutException
                    || e.getCause() instanceof java.net.SocketTimeoutException;
            throw new AiClientException(timeout ? Kind.TIMEOUT : Kind.SERVER_ERROR, "AI 서버에 연결하지 못했어요.", e);
        } catch (RuntimeException e) {
            // 응답을 해석하지 못한 경우 등
            throw new AiClientException(Kind.SERVER_ERROR, "AI 서버 응답을 처리하지 못했어요.", e);
        }
    }

    private static AiClientException toException(HttpStatusCode status) {
        if (status.value() == 422) {
            return new AiClientException(Kind.TEXT_NOT_RECOGNIZED, "AI가 문서에서 글자를 인식하지 못했어요.");
        }
        if (status.is5xxServerError()) {
            return new AiClientException(Kind.SERVER_ERROR, "AI 서버 오류: " + status.value());
        }
        return new AiClientException(Kind.REJECTED, "AI 서버가 요청을 거절했어요: " + status.value());
    }
}
