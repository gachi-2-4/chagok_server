package gachi.chagok_server.domain.ai.client;

import gachi.chagok_server.domain.ai.client.AiClientException.Kind;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.ChatAnswer;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.Suggestion;
import gachi.chagok_server.domain.ai.dto.AiChatDtos.Suggestions;
import gachi.chagok_server.domain.ai.dto.AiComparisonDtos.Comparison;
import gachi.chagok_server.domain.ai.dto.AiComparisonDtos.InquiryMessage;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.SummaryCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Spring RestClient로 AI 서버를 부르는 구현. 호출마다 AI 명세의 타임아웃이 달라 RestClient를 따로 둔다.
 * 경로는 AI 명세 페이지 기준이다 (서류 업로드·채팅 본문 설명의 다른 경로와 다름 — 팀 확정 필요).
 */
@Component
public class RestClientAiServerClient implements AiServerClient {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SUGGESTION_TIMEOUT = Duration.ofSeconds(10);
    /** AI 20초 + 여유 */
    private static final Duration COMPARE_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration INQUIRY_TIMEOUT = Duration.ofSeconds(20);

    private final RestClient analysisClient;
    private final RestClient summaryClient;
    private final RestClient chatClient;
    private final RestClient suggestionClient;
    private final RestClient compareClient;
    private final RestClient inquiryClient;
    private final String apiKey;
    private final JsonMapper jsonMapper;

    public RestClientAiServerClient(@Value("${chagok.ai.base-url}") String baseUrl,
                                    @Value("${chagok.ai.api-key}") String apiKey,
                                    @Value("${chagok.ai.analysis-timeout-seconds:90}") int analysisTimeoutSeconds,
                                    @Value("${chagok.ai.summary-timeout-seconds:15}") int summaryTimeoutSeconds,
                                    JsonMapper jsonMapper) {
        this.apiKey = apiKey;
        this.jsonMapper = jsonMapper;
        this.analysisClient = build(baseUrl, Duration.ofSeconds(analysisTimeoutSeconds));
        this.summaryClient = build(baseUrl, Duration.ofSeconds(summaryTimeoutSeconds));
        this.chatClient = build(baseUrl, CHAT_TIMEOUT);
        this.suggestionClient = build(baseUrl, SUGGESTION_TIMEOUT);
        this.compareClient = build(baseUrl, COMPARE_TIMEOUT);
        this.inquiryClient = build(baseUrl, INQUIRY_TIMEOUT);
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
                .onStatus(HttpStatusCode::isError, this::throwFor)
                .body(Analysis.class));
    }

    @Override
    public SummaryCard summaryCard(Long documentId, Long userId) {
        // 명세는 GET + JSON Body라 서버·클라이언트에 따라 Body가 무시될 수 있다. POST로 보낸다 (AI 팀과 확정 필요).
        return post(summaryClient, "/ai/documents/summary-card", Map.of("documentId", documentId, "userId", userId), SummaryCard.class);
    }

    @Override
    public ChatAnswer chatAnswer(Long chatId, Long userId, List<Long> documentIds, String question) {
        // 채팅 Body는 명세 페이지마다 다르다(documentId 단수 vs documentIds 복수). 앱 연동 명세대로 복수로 보낸다.
        Map<String, Object> body = Map.of("userId", userId, "documentIds", documentIds, "question", question);
        return post(chatClient, "/ai/chats/" + chatId + "/answer", body, ChatAnswer.class);
    }

    @Override
    public List<Suggestion> suggestions(Long userId, Long documentId, int count) {
        Suggestions result = call(() -> suggestionClient.get()
                .uri(uri -> uri.path("/ai/chat/suggestions")
                        .queryParam("userId", userId)
                        .queryParam("documentId", documentId)
                        .queryParam("count", count)
                        .build())
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwFor)
                .body(Suggestions.class));
        return result.suggestions() == null ? List.of() : result.suggestions();
    }

    @Override
    public Comparison compare(Long comparisonId, Long userId, Long baseDocumentId, Long targetDocumentId, String compareType) {
        Map<String, Object> body = Map.of("comparisonId", comparisonId, "userId", userId, "baseDocumentId", baseDocumentId,
                "targetDocumentId", targetDocumentId, "compareType", compareType);
        return post(compareClient, "/ai/comparisons", body, Comparison.class);
    }

    @Override
    public InquiryMessage inquiryMessage(Long comparisonId, Long userId, String tone, String extraRequest) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        if (tone != null) {
            body.put("tone", tone);
        }
        if (extraRequest != null) {
            body.put("extraRequest", extraRequest);
        }
        return post(inquiryClient, "/ai/comparisons/" + comparisonId + "/inquiry-messages", body, InquiryMessage.class);
    }

    private <T> T post(RestClient client, String path, Object body, Class<T> type) {
        return call(() -> client.post()
                .uri(path)
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwFor)
                .body(type));
    }

    private static <T> T call(Supplier<T> request) {
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

    /** 오류 응답을 읽어 BE가 처리할 수 있는 예외로 바꾼다. */
    private void throwFor(org.springframework.http.HttpRequest request, ClientHttpResponse response) throws IOException {
        throw toException(response.getStatusCode(), readBody(response));
    }

    private JsonNode readBody(ClientHttpResponse response) {
        try {
            return jsonMapper.readTree(response.getBody());
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    static AiClientException toException(HttpStatusCode status, JsonNode body) {
        String code = text(body, "code");
        String reason = text(body, "reason");
        int value = status.value();

        if (value == 422) {
            if ("NOT_COMPARABLE".equals(code)) {
                return new AiClientException(Kind.NOT_COMPARABLE, "비교할 수 없는 서류예요.").withReason(reason);
            }
            if ("NO_DIFFERENCE".equals(code)) {
                return new AiClientException(Kind.NO_DIFFERENCE, "차이가 없어요.");
            }
            // 문서 분석의 422는 글자를 인식하지 못했다는 뜻이다 (코드가 오지 않아도 그렇게 본다).
            return new AiClientException(Kind.TEXT_NOT_RECOGNIZED, "AI가 문서에서 글자를 인식하지 못했어요.");
        }
        if (value == 404 && "INDEX_NOT_FOUND".equals(code)) {
            return new AiClientException(Kind.INDEX_NOT_FOUND, "AI가 서류를 찾지 못했어요.");
        }
        if (value == 429) {
            return new AiClientException(Kind.RATE_LIMITED, "AI 서버 요청 횟수 제한에 걸렸어요.");
        }
        if (status.is5xxServerError()) {
            return new AiClientException(Kind.SERVER_ERROR, "AI 서버 오류: " + value);
        }
        return new AiClientException(Kind.REJECTED, "AI 서버가 요청을 거절했어요: " + value);
    }

    private static String text(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field)) {
            return null;
        }
        return body.get(field).asString();
    }
}
