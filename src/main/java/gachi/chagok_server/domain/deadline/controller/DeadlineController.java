package gachi.chagok_server.domain.deadline.controller;

import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.*;
import gachi.chagok_server.domain.deadline.service.DeadlineService;
import gachi.chagok_server.global.common.ApiResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deadlines")
public class DeadlineController {

    private final DeadlineService deadlineService;
    private final JsonMapper jsonMapper;

    @GetMapping
    public ApiResponse<ListResponse> list(@AuthenticationPrincipal Long userId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "true") boolean includeCompleted,
                                          @RequestParam(required = false) Long boxId,
                                          @RequestParam(required = false) Long documentId) {
        return ApiResponse.ok(deadlineService.list(userId, from, to, includeCompleted, boxId, documentId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Response>> create(@AuthenticationPrincipal Long userId,
                                                        @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(deadlineService.create(userId, request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Response> update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                        @RequestBody JsonNode body) {
        return ApiResponse.ok(deadlineService.update(userId, id, parseUpdate(body)));
    }

    /**
     * PATCH Body를 읽는다. 보내지 않은 항목과 null을 보낸 항목을 구분해야 해서(null은 "비움")
     * 값은 일반 DTO로 읽고, null을 명시한 반복·종료일·금액 이름만 따로 모은다.
     */
    private UpdateRequest parseUpdate(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            Set<String> clear = new HashSet<>();
            for (String name : List.of("recurrence", "endDate", "amount")) {
                if (body.has(name) && body.get(name).isNull()) {
                    clear.add(name);
                }
            }
            return jsonMapper.treeToValue(body, UpdateRequest.class).withClear(clear);
        } catch (JacksonException e) {
            // 코드표에 없는 값, 날짜 형식 오류 등
            throw new BusinessException(ErrorCode.INVALID_INPUT, e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        deadlineService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
