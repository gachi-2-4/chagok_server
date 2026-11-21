package gachi.chagok_server.domain.comparison.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiClientException.Kind;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiComparisonDtos;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.CompareRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.InquiryRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Response;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Tone;
import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.Comparison;
import gachi.chagok_server.domain.comparison.entity.ComparisonStatus;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    private static final Long USER_ID = 1L;
    private final CompareRequest request = new CompareRequest(488L, 533L, CompareType.BILL);

    @Mock ComparisonStore store;
    @Mock AiServerClient ai;
    @InjectMocks ComparisonService service;

    private static AiComparisonDtos.Comparison aiResult() {
        return new AiComparisonDtos.Comparison(77L, 488L, 533L, "BILL", "MISMATCH", "약속과 다른 금액이 있어요",
                Map.of(), List.of(), List.of(), "추정 결과");
    }

    private static void assertError(Runnable action, ErrorCode expected, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(expected);
            assertThat(e.getReason()).isEqualTo(reason);
        });
    }

    @Test
    @DisplayName("비교 ID를 만들고 AI에 보낸 뒤 결과를 저장한다")
    void compare() {
        // given
        Response saved = new Response(77L, CompareType.BILL, ComparisonStatus.MISMATCH, "약속과 다른 금액이 있어요", null, null,
                Map.of(), List.of(), List.of(), "추정 결과", false, null);
        given(store.reserve(USER_ID, request)).willReturn(77L);
        AiComparisonDtos.Comparison result = aiResult();
        given(ai.compare(77L, USER_ID, 488L, 533L, "BILL")).willReturn(result);
        given(store.complete(USER_ID, 77L, result)).willReturn(saved);

        // when
        Response response = service.compare(USER_ID, request);

        // then
        assertThat(response).isSameAs(saved);
    }

    @Test
    @DisplayName("AI가 비교할 수 없다고 하면 이유와 함께 422로 알리고 미리 만든 행을 지운다")
    void notComparable() {
        // given
        given(store.reserve(USER_ID, request)).willReturn(77L);
        given(ai.compare(any(), any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.NOT_COMPARABLE, "422").withReason("DIFFERENT_PROVIDER"));

        // when & then
        assertError(() -> service.compare(USER_ID, request), ErrorCode.NOT_COMPARABLE, "DIFFERENT_PROVIDER");
        verify(store).discard(77L);
        verify(store, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("AI 시간 초과는 504, 그 밖의 AI 오류는 502")
    void aiFailures() {
        // given
        given(store.reserve(USER_ID, request)).willReturn(77L);
        given(ai.compare(any(), any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.TIMEOUT, "timeout"))
                .willThrow(new AiClientException(Kind.SERVER_ERROR, "500"));

        // when & then
        assertError(() -> service.compare(USER_ID, request), ErrorCode.AI_TIMEOUT, null);
        assertError(() -> service.compare(USER_ID, request), ErrorCode.AI_ERROR, null);
        verify(store, org.mockito.Mockito.times(2)).discard(77L);
    }

    @Test
    @DisplayName("검사에서 걸리면 AI를 부르지 않는다")
    void validationFailureSkipsAi() {
        // given
        given(store.reserve(USER_ID, request)).willThrow(new BusinessException(ErrorCode.INVALID_COMPARE_PAIR));

        // when & then
        assertError(() -> service.compare(USER_ID, request), ErrorCode.INVALID_COMPARE_PAIR, null);
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    @Test
    @DisplayName("확인 요청 문자는 어조와 추가 요청을 AI에 그대로 전달한다")
    void inquiryMessage() {
        // given
        Comparison comparison = comparison(ComparisonStatus.MISMATCH);
        given(store.find(USER_ID, 77L)).willReturn(comparison);
        AiComparisonDtos.InquiryMessage message = new AiComparisonDtos.InquiryMessage(77L,
                Map.of("text", "안녕하세요.", "type", "SMS", "byteLength", 12), "FRIENDLY");
        given(ai.inquiryMessage(77L, USER_ID, "FRIENDLY", "할인 종료 여부도 물어봐 주세요")).willReturn(message);

        // when
        AiComparisonDtos.InquiryMessage result = service.inquiryMessage(USER_ID, 77L,
                new InquiryRequest(Tone.FRIENDLY, "  할인 종료 여부도 물어봐 주세요 "));

        // then
        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("차이가 없는(MATCH) 비교는 AI를 부르지 않고 NO_DIFFERENCE")
    void inquiryForMatch() {
        // given
        given(store.find(USER_ID, 77L)).willReturn(comparison(ComparisonStatus.MATCH));

        // when & then
        assertError(() -> service.inquiryMessage(USER_ID, 77L, new InquiryRequest(null, null)), ErrorCode.NO_DIFFERENCE, null);
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    @Test
    @DisplayName("추가 요청은 200자까지만 받는다")
    void extraRequestTooLong() {
        assertError(() -> service.inquiryMessage(USER_ID, 77L, new InquiryRequest(null, "가".repeat(201))),
                ErrorCode.INVALID_INPUT, null);
    }

    @Test
    @DisplayName("문자 생성 중 AI의 NO_DIFFERENCE·시간 초과도 앱 오류로 바꿔 준다")
    void inquiryAiErrors() {
        // given
        given(store.find(USER_ID, 77L)).willReturn(comparison(ComparisonStatus.MISMATCH));
        given(ai.inquiryMessage(any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.NO_DIFFERENCE, "no diff"))
                .willThrow(new AiClientException(Kind.TIMEOUT, "timeout"));

        // when & then
        assertError(() -> service.inquiryMessage(USER_ID, 77L, new InquiryRequest(null, null)), ErrorCode.NO_DIFFERENCE, null);
        assertError(() -> service.inquiryMessage(USER_ID, 77L, new InquiryRequest(null, null)), ErrorCode.AI_TIMEOUT, null);
    }

    private Comparison comparison(ComparisonStatus status) {
        Comparison comparison = new Comparison(User.phoneUser("01012345678", "hash", "홍길동"), 488L, 533L, CompareType.BILL);
        comparison.complete(status, "{}");
        return comparison;
    }
}
