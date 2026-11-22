package gachi.chagok_server.domain.auth.service;
import gachi.chagok_server.domain.auth.entity.*;
import gachi.chagok_server.domain.auth.repository.*;
import gachi.chagok_server.domain.auth.sms.SmsSender;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;import java.security.*;import javax.crypto.*;import javax.crypto.spec.*;import java.nio.charset.StandardCharsets;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PhoneVerificationService {
 private final PhoneVerificationRepository codes;
 private final VerificationGrantRepository grants;
 private final UserRepository users;
 private final SmsSender sms;
 private final SecureRandom random = new SecureRandom();
 @Value("${auth.sms.hash-secret}") private String secret;
 // log 모드(시연·테스트용)에서만 인증번호를 응답에 포함한다. 실제 문자 발송 모드에서는 절대 노출하지 않는다.
 @Value("${auth.sms.provider:twilio}") private String smsProvider;
 private String hash(String raw){try {Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(raw.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 @Transactional
 public VerificationCodeResponse send(String phone,VerificationPurpose purpose){
   if(purpose==VerificationPurpose.SIGNUP&&users.existsByPhoneNumber(phone))throw new BusinessException(ErrorCode.CONFLICT);
   // Password reset deliberately never reveals whether the phone exists.
   boolean eligible=purpose==VerificationPurpose.SIGNUP||users.findByPhoneNumber(phone).filter(u->!u.isDeleted()&&u.getPasswordHash()!=null).isPresent();
   if(!eligible)return new VerificationCodeResponse(phone,180,60);
   Instant now=Instant.now();PhoneVerification v=codes.findLocked(phone,purpose).orElse(null);
   if(v!=null){if(v.getLastSentAt().plusSeconds(60).isAfter(now)||v.getSentToday()>=10&&v.getSentDate().equals(now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()))throw new BusinessException(ErrorCode.CONFLICT);}
   String code="%06d".formatted(random.nextInt(1_000_000));
   if(v==null){v=new PhoneVerification(phone,purpose,hash(phone+":"+purpose+":"+code),now);codes.saveAndFlush(v);}else{v.resend(hash(phone+":"+purpose+":"+code),now);}
   sms.send(phone,"[차곡] 인증번호 "+code+" (3분 이내 입력)");
   return new VerificationCodeResponse(phone,180,60,"log".equals(smsProvider)?code:null);
 }
 @Transactional(noRollbackFor=BusinessException.class)
 public VerificationTokenResponse confirm(String phone,String code,VerificationPurpose purpose){
   // 404를 주면 비밀번호 재설정 대상(가입된 번호) 여부가 드러나므로, 코드 불일치와 같은 400으로 응답한다.
   PhoneVerification v=codes.findLocked(phone,purpose).orElseThrow(()->new BusinessException(ErrorCode.INVALID_INPUT));
   if(v.isVerified()||!v.getExpiresAt().isAfter(Instant.now())||v.getFailedAttempts()>=5)throw new BusinessException(ErrorCode.INVALID_INPUT);
   byte[] a=v.getCodeHash().getBytes(StandardCharsets.UTF_8), b=hash(phone+":"+purpose+":"+code).getBytes(StandardCharsets.UTF_8);
   if(!MessageDigest.isEqual(a,b)){v.failed();throw new BusinessException(ErrorCode.INVALID_INPUT);}
   v.markVerified();String token="vt_"+UUID.randomUUID().toString().replace("-","");
   grants.save(new VerificationGrant(hash(token),phone,purpose,Instant.now().plusSeconds(600)));
   return new VerificationTokenResponse(token,600);
 }
 @Transactional
 public void consume(String phone,String token,VerificationPurpose purpose){
   VerificationGrant grant=grants.findLocked(hash(token)).orElseThrow(()->new BusinessException(ErrorCode.INVALID_INPUT));
   if(!grant.getPhoneNumber().equals(phone)||grant.getPurpose()!=purpose||!grant.getExpiresAt().isAfter(Instant.now()))throw new BusinessException(ErrorCode.INVALID_INPUT);
   grants.delete(grant);grants.flush();
 }
}
