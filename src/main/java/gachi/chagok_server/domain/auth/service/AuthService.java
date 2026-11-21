package gachi.chagok_server.domain.auth.service;
import gachi.chagok_server.domain.auth.dto.request.*;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.auth.entity.VerificationPurpose;
import gachi.chagok_server.domain.user.entity.*;
import gachi.chagok_server.domain.user.repository.*;
import gachi.chagok_server.domain.notification.entity.NotificationSettings;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.domain.notification.repository.PushTokenRepository;
import gachi.chagok_server.domain.box.service.BoxService;
import gachi.chagok_server.global.exception.*;
import lombok.RequiredArgsConstructor;import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AuthService {
 private final UserRepository users;private final PasswordEncoder encoder;private final PhoneVerificationService phones;
 private final TokenService tokens;private final ConsentHistoryRepository consents;private final NotificationSettingsRepository notificationSettings;
 private final PushTokenRepository pushTokens;
 private final BoxService boxes;
 @Value("${chagok.consents.version:2026-11-01}") private String consentVersion;
 @Transactional
 public TokenResponse signUp(SignUpRequest req){
  if(users.existsByPhoneNumber(req.phoneNumber()))throw new BusinessException(ErrorCode.CONFLICT);
  if(!req.consents().termsOfService()||!req.consents().privacyPolicy())throw new BusinessException(ErrorCode.INVALID_INPUT);
  if(req.birthDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))throw new BusinessException(ErrorCode.INVALID_INPUT);
  if(req.birthDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(14))&&!req.consents().guardianConsent())throw new BusinessException(ErrorCode.INVALID_INPUT);
  phones.consume(req.phoneNumber(),req.verificationToken(),VerificationPurpose.SIGNUP);
  User u=User.phoneUser(req.phoneNumber(),encoder.encode(req.password()),req.name());u.setBirthDateOnce(req.birthDate());users.saveAndFlush(u);
  saveConsent(u,ConsentType.TERMS_OF_SERVICE,true);saveConsent(u,ConsentType.PRIVACY_POLICY,true);
  saveConsent(u,ConsentType.GUARDIAN_CONSENT,req.consents().guardianConsent());saveConsent(u,ConsentType.OVERSEAS_TRANSFER,req.consents().overseasTransfer());saveConsent(u,ConsentType.MARKETING,req.consents().marketing());
  notificationSettings.save(new NotificationSettings(u));
  boxes.createDefaults(u);
  return tokens.issue(u,true);
 }
 private void saveConsent(User u,ConsentType t,boolean v){consents.save(new ConsentHistory(u,t,v,consentVersion));}
 @Transactional
 public TokenResponse login(LoginRequest req){
  User u=users.findByPhoneNumber(req.phoneNumber()).filter(x->!x.isDeleted()).orElse(null);
  if(u==null||u.getPasswordHash()==null||!encoder.matches(req.password(),u.getPasswordHash()))throw new BusinessException(ErrorCode.UNAUTHORIZED);
  return tokens.issue(u,req.keepLogin());
 }
 @Transactional
 public void reset(PasswordResetRequest req){
  phones.consume(req.phoneNumber(),req.verificationToken(),VerificationPurpose.PASSWORD_RESET);
  User u=users.findByPhoneNumber(req.phoneNumber()).filter(x->!x.isDeleted()).orElseThrow(()->new BusinessException(ErrorCode.INVALID_INPUT));
  if(u.getPasswordHash()==null||encoder.matches(req.newPassword(),u.getPasswordHash()))throw new BusinessException(ErrorCode.INVALID_INPUT);
  u.changePasswordHash(encoder.encode(req.newPassword()));tokens.revokeAll(u.getId());
 }
 /** 로그아웃. 푸시 토큰은 리프레시 세션의 소유자와 같은 사용자의 기기일 때만 삭제한다(타인 기기 토큰 삭제 방지). */
 @Transactional
 public void logout(LogoutRequest req,String accessToken){
  tokens.logout(req.refreshToken(),accessToken).ifPresent(userId->pushTokens.deleteByDeviceIdAndUserId(req.deviceId(),userId));
 }
}
