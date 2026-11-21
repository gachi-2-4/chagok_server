package gachi.chagok_server.domain.deadline.controller;

import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.*;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReminderResponse>> create(@AuthenticationPrincipal Long userId,
                                                                @Valid @RequestBody ReminderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reminderService.create(userId, request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ReminderResponse> update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                @RequestBody ReminderUpdateRequest request) {
        return ApiResponse.ok(reminderService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        reminderService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
