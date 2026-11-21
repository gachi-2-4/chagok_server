package gachi.chagok_server.domain.auth.service;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.auth.entity.*;
import gachi.chagok_server.domain.auth.repository.*;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.exception.*;
import gachi.chagok_server.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;import java.security.*;import java.time.Instant;import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class TokenService {
 private final JwtTokenProvider jwt;
 private final RefreshSessionRepository sessions;
 private final RevokedAccessTokenRepository revoked;
 private final SecureRandom random =new SecureRandom();
 private String randomToken(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @Transactional
 public TokenResponse issue(User user,boolean autoLogin){String refresh=randomToken();long seconds=autoLogin?1209600:86400;sessions.save(new RefreshSession(user,hash(refresh),Instant.now().plusSeconds(seconds),autoLogin));return new TokenResponse(user.getId(),jwt.issue(user.getId()),refresh,JwtTokenProvider.ACCESS_TTL,seconds,user.isOnboardingCompleted());}
 @Transactional(noRollbackFor=BusinessException.class)
 public RefreshResponse refresh(String refresh){RefreshSession session=sessions.findLockedByTokenHash(hash(refresh)).orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED));
 // 만료된 세션이나 탈퇴한 사용자의 세션은 삭제하고 재로그인을 요구한다.
 if(!session.getExpiresAt().isAfter(Instant.now())||session.getUser().isDeleted()){sessions.delete(session);throw new BusinessException(ErrorCode.UNAUTHORIZED);}
 String next=randomToken();long seconds=session.isAutoLogin()?1209600:86400;session.rotate(hash(next),Instant.now().plusSeconds(seconds));return new RefreshResponse(jwt.issue(session.getUser().getId()),next,JwtTokenProvider.ACCESS_TTL,seconds);}
 /** 리프레시 세션을 삭제하고 액세스 토큰을 폐기한다. 세션이 존재했다면 그 세션의 사용자 ID를 반환한다. */
 @Transactional
 public Optional<Long> logout(String refresh,String access){
  Optional<Long> userId=sessions.findByTokenHash(hash(refresh)).map(s->{Long id=s.getUser().getId();sessions.delete(s);return id;});
  if(access!=null&&!access.isBlank())try{Instant exp=jwt.expiry(access);if(exp.isAfter(Instant.now()))revoked.save(new RevokedAccessToken(hash(access),exp));}catch(Exception ignored){}
  return userId;
 }
 @Transactional public void revokeAll(Long userId){sessions.deleteByUserId(userId);}
 public boolean isRevoked(String token){return revoked.findById(hash(token)).map(e->e.getExpiresAt().isAfter(Instant.now())).orElse(false);}
}
