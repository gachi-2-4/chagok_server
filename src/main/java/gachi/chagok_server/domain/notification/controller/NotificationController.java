package gachi.chagok_server.domain.notification.controller;
import gachi.chagok_server.domain.notification.dto.NotificationDtos.*;
import gachi.chagok_server.domain.notification.service.NotificationService;
import gachi.chagok_server.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/notifications")
public class NotificationController {
 private final NotificationService service;
 @GetMapping public ApiResponse<ListResult> list(@AuthenticationPrincipal Long userId,@RequestParam(defaultValue="ALL") Filter filter,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.list(userId,filter,page,size));}
 @GetMapping("/settings") public ApiResponse<SettingsResult> settings(@AuthenticationPrincipal Long userId){return ApiResponse.ok(service.settings(userId));}
 @PatchMapping("/settings") public ApiResponse<SettingsResult> changeSettings(@AuthenticationPrincipal Long userId,@RequestBody SettingsRequest r){return ApiResponse.ok(service.changeSettings(userId,r));}
 @PatchMapping("/read-all") public ApiResponse<AllReadResult> readAll(@AuthenticationPrincipal Long userId){return ApiResponse.ok(service.readAll(userId));}
 @PutMapping("/push-tokens/{deviceId}") public ResponseEntity<Void> push(@AuthenticationPrincipal Long userId,@PathVariable String deviceId,@Valid @RequestBody PushRequest r){service.pushToken(userId,deviceId,r);return ResponseEntity.noContent().build();}
 @GetMapping("/{id}") public ApiResponse<Item> detail(@AuthenticationPrincipal Long userId,@PathVariable Long id){return ApiResponse.ok(service.detail(userId,id));}
 @PatchMapping("/{id}") public ApiResponse<UpdateResult> update(@AuthenticationPrincipal Long userId,@PathVariable Long id,@RequestBody UpdateRequest r){return ApiResponse.ok(service.update(userId,id,r));}
}
