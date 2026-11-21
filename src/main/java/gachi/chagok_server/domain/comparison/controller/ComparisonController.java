package gachi.chagok_server.domain.comparison.controller;

import gachi.chagok_server.domain.ai.dto.AiComparisonDtos.InquiryMessage;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.CompareRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.InquiryRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Response;
import gachi.chagok_server.domain.comparison.service.ComparisonService;
import gachi.chagok_server.global.common.ApiResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.security.InternalKeyVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final InternalKeyVerifier internalKey;

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> compare(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody CompareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comparisonService.compare(userId, request)));
    }

    /**
     * 비교 결과 조회. 앱은 Bearer 토큰으로, AI 서버(확인 요청 문자 생성)는 X-Internal-Key + userId 쿼리로 부른다.
     */
    @GetMapping("/{id}")
    public ApiResponse<Response> get(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                     @RequestHeader(value = InternalKeyVerifier.HEADER, required = false) String key,
                                     @RequestParam(name = "userId", required = false) Long ownerId) {
        if (key != null) {
            internalKey.verify(key);
            if (ownerId == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return ApiResponse.ok(comparisonService.get(ownerId, id));
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(comparisonService.get(userId, id));
    }

    @PostMapping("/{id}/inquiry-messages")
    public ResponseEntity<ApiResponse<InquiryMessage>> inquiryMessage(@AuthenticationPrincipal Long userId,
                                                                      @PathVariable Long id,
                                                                      @RequestBody(required = false) InquiryRequest request) {
        InquiryRequest body = request != null ? request : new InquiryRequest(null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comparisonService.inquiryMessage(userId, id, body)));
    }
}
