package gachi.chagok_server.domain.user.controller;
import gachi.chagok_server.domain.user.dto.UserDtos.*;
import gachi.chagok_server.domain.user.service.UserService;
import gachi.chagok_server.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/user")
public class UserController {
 private final UserService service;
 @GetMapping("/me") public ApiResponse<MyInfo> me(@AuthenticationPrincipal Long userId){return ApiResponse.ok(service.me(userId));}
 @PatchMapping("/me/preferences") public ApiResponse<FontResult> font(@AuthenticationPrincipal Long userId,@Valid @RequestBody FontRequest r){return ApiResponse.ok(service.changeFont(userId,r));}
 @PutMapping("/me/profile") public ApiResponse<ProfileResult> profile(@AuthenticationPrincipal Long userId,@Valid @RequestBody ProfileRequest r){return ApiResponse.ok(service.saveProfile(userId,r));}
 @PostMapping("/me/consents") public ApiResponse<ConsentResult> consents(@AuthenticationPrincipal Long userId,@RequestBody ConsentRequest r){return ApiResponse.ok(service.consent(userId,r));}
 // DELETE /me deliberately NOT exposed: spec requires full cascading file, AI, token deletion not present in current repo.
}
