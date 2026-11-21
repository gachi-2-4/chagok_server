package gachi.chagok_server.domain.auth.controller;
import gachi.chagok_server.domain.auth.dto.request.*;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.auth.entity.VerificationPurpose;
import gachi.chagok_server.domain.auth.service.*;
import gachi.chagok_server.global.common.ApiResponse;
import gachi.chagok_server.global.exception.*;
import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService auth;private final PhoneVerificationService phone;private final OAuthService oauth;
 private final TokenService tokens;
 @PostMapping("/phone/verifications") public ApiResponse<VerificationCodeResponse> send(@Valid @RequestBody PhoneRequest r){return ApiResponse.ok(phone.send(r.phoneNumber(),VerificationPurpose.SIGNUP));}
 @PostMapping("/password/reset-requests") public ApiResponse<VerificationCodeResponse> resetRequest(@Valid @RequestBody PhoneRequest r){return ApiResponse.ok(phone.send(r.phoneNumber(),VerificationPurpose.PASSWORD_RESET));}
 @PostMapping("/phone/verifications/confirm") public ApiResponse<VerificationTokenResponse> confirm(@Valid @RequestBody VerifyPhoneRequest r){return ApiResponse.ok(phone.confirm(r.phoneNumber(),r.code(),r.purpose()));}
 @PostMapping("/signup") public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignUpRequest r){return ApiResponse.ok(auth.signUp(r));}
 @PostMapping("/login") public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest r){return ApiResponse.ok(auth.login(r));}
 @PostMapping("/kakao") public ApiResponse<OAuthTokenResponse> kakao(@RequestBody OAuthLoginRequest r){if(r.kakaoAccessToken()==null||r.kakaoAccessToken().isBlank())throw new BusinessException(ErrorCode.INVALID_INPUT);return ApiResponse.ok(oauth.kakaoLogin(r.kakaoAccessToken(),r.keepLogin()));}
 @PostMapping("/google") public ApiResponse<OAuthTokenResponse> google(@RequestBody OAuthLoginRequest r){if(r.googleIdToken()==null||r.googleIdToken().isBlank())throw new BusinessException(ErrorCode.INVALID_INPUT);return ApiResponse.ok(oauth.googleLogin(r.googleIdToken(),r.keepLogin()));}
 @PostMapping("/token/refresh") public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest r){return ApiResponse.ok(tokens.refresh(r.refreshToken()));}
 @PostMapping("/password/resets") public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest r){auth.reset(r);return ResponseEntity.noContent().build();}
 @PostMapping("/logout") public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest r,@RequestHeader(value="Authorization",required=false) String header){auth.logout(r,header!=null&&header.startsWith("Bearer ")?header.substring(7):null);return ResponseEntity.noContent().build();}
}
