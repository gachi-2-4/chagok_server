package gachi.chagok_server.domain.deadline.controller;

import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.*;
import gachi.chagok_server.domain.deadline.service.DeadlineService;
import gachi.chagok_server.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deadlines")
public class DeadlineController {

    private final DeadlineService deadlineService;

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
                                        @RequestBody UpdateRequest request) {
        return ApiResponse.ok(deadlineService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        deadlineService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
