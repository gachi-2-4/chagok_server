package gachi.chagok_server.domain.comparison.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiComparisonDtos;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.CompareRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.InquiryRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Response;
import gachi.chagok_server.domain.comparison.entity.Comparison;
import gachi.chagok_server.domain.comparison.entity.ComparisonStatus;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 서류 비교 요청과 확인 요청 문자 만들기. AI 서버 호출(최대 25초)이 DB 연결을 붙잡지 않도록
 * 이 클래스는 트랜잭션을 열지 않고, 저장은 {@link ComparisonStore}의 짧은 트랜잭션에 맡긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    static final int MAX_EXTRA_REQUEST_LENGTH = 200;

    private final ComparisonStore store;
    private final AiServerClient ai;

    public Response compare(Long userId, CompareRequest request) {
        Long comparisonId = store.reserve(userId, request);
        AiComparisonDtos.Comparison result;
        try {
            result = ai.compare(comparisonId, userId, request.baseDocumentId(), request.targetDocumentId(),
                    request.compareType().name());
        } catch (AiClientException e) {
            store.discard(comparisonId);
            throw translate(e);
        } catch (RuntimeException e) {
            store.discard(comparisonId);
            throw e;
        }
        return store.complete(userId, comparisonId, result);
    }

    public Response get(Long userId, Long comparisonId) {
        return store.get(userId, comparisonId);
    }

    /**
     * 확인 요청 문자를 만든다. 사용자가 복사해서 보내므로 자동 전송하지 않고, 다시 만들기는 같은 요청을 다시 보내면 된다.
     * 차이가 없는(MATCH) 비교는 AI를 부르지 않고 422를 준다.
     */
    public AiComparisonDtos.InquiryMessage inquiryMessage(Long userId, Long comparisonId, InquiryRequest request) {
        String extra = request.extraRequest() == null ? null : request.extraRequest().trim();
        if (extra != null && extra.length() > MAX_EXTRA_REQUEST_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Comparison comparison = store.find(userId, comparisonId);
        if (comparison.getStatus() == ComparisonStatus.MATCH) {
            throw new BusinessException(ErrorCode.NO_DIFFERENCE);
        }
        try {
            return ai.inquiryMessage(comparisonId, userId, request.tone() == null ? null : request.tone().name(),
                    extra == null || extra.isEmpty() ? null : extra);
        } catch (AiClientException e) {
            throw translate(e);
        }
    }

    /** AI 서버 오류를 앱에 줄 오류로 바꾼다: 422는 그대로, 시간 초과는 504, 나머지는 502. */
    static BusinessException translate(AiClientException e) {
        return switch (e.getKind()) {
            case NOT_COMPARABLE -> new BusinessException(ErrorCode.NOT_COMPARABLE, e.getReason());
            case NO_DIFFERENCE -> new BusinessException(ErrorCode.NO_DIFFERENCE);
            case TIMEOUT -> new BusinessException(ErrorCode.AI_TIMEOUT, e);
            default -> new BusinessException(ErrorCode.AI_ERROR, e);
        };
    }
}
