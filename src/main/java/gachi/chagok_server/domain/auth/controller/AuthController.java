package gachi.chagok_server.domain.auth.controller;
import gachi.chagok_server.domain.auth.dto.request.*;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.auth.entity.VerificationPurpose;
import gachi.chagok_server.domain.auth.service.*;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService auth;private final PhoneVerificationService phone;private final OAuthService oauth;
 private final TokenService tokens;private final PushTokenRepository pushTokens;
 @PostMapping("/phone/verifications") public VerificationCodeResponse send(@Valid @RequestBody PhoneRequest r){return phone.send(r.phoneNumber(),VerificationPurpose.SIGNUP);}
 @PostMapping("/password/reset-requests") public VerificationCodeResponse resetRequest(@Valid @RequestBody PhoneRequest r){return phone.send(r.phoneNumber(),VerificationPurpose.PASSWORD_RESET);}
 @PostMapping("/phone/verifications/confirm") public VerificationTokenResponse confirm(@Valid @RequestBody VerifyPhoneRequest r){return phone.confirm(r.phoneNumber(),r.code(),r.purpose());}
 @PostMapping("/signup") public TokenResponse signup(@Valid @RequestBody SignUpRequest r){return auth.signUp(r);}
 @PostMapping("/login") public TokenResponse login(@Valid @RequestBody LoginRequest r){return auth.login(r);}
 @PostMapping("/kakao") public OAuthTokenResponse kakao(@RequestBody OAuthLoginRequest r){if(r.kakaoAccessToken()==null||r.kakaoAccessToken().isBlank())throw new IllegalArgumentException();return oauth.kakaoLogin(r.kakaoAccessToken(),r.keepLogin());}
 @PostMapping("/google") public OAuthTokenResponse google(@RequestBody OAuthLoginRequest r){if(r.googleIdToken()==null||r.googleIdToken().isBlank())throw new IllegalArgumentException();return oauth.googleLogin(r.googleIdToken(),r.keepLogin());}
 @PostMapping("/token/refresh") public RefreshResponse refresh(@Valid @RequestBody RefreshRequest r){return tokens.refresh(r.refreshToken());}
 @PostMapping("/password/resets") public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest r){auth.reset(r);return ResponseEntity.noContent().build();}
 @PostMapping("/logout") public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest r,@RequestHeader(value="Authorization",required=false) String header){tokens.logout(r.refreshToken(),header!=null&&header.startsWith("Bearer ")?header.substring(7):null);pushTokens.deleteById(r.deviceId());return ResponseEntity.noContent().build();}
}
