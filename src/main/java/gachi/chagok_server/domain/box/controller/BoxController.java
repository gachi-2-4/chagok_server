package gachi.chagok_server.domain.box.controller;

import gachi.chagok_server.domain.box.dto.BoxDtos.*;
import gachi.chagok_server.domain.box.service.BoxService;
import gachi.chagok_server.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/boxes")
public class BoxController {

    private final BoxService boxService;

    @GetMapping
    public ApiResponse<ListResponse> list(@AuthenticationPrincipal Long userId,
                                          @RequestParam(defaultValue = "CREATED") Sort sort) {
        return ApiResponse.ok(boxService.list(userId, sort));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Created>> create(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(boxService.create(userId, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(boxService.open(userId, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Created> update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                       @RequestBody UpdateRequest request) {
        return ApiResponse.ok(boxService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Deleted> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(boxService.delete(userId, id));
    }
}
