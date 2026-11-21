package gachi.chagok_server.domain.auth.service;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.auth.entity.*;
import gachi.chagok_server.domain.auth.repository.*;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.exception.*;
import gachi.chagok_server.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;import java.security.*;import java.time.Instant;import java.util.*;
@Service @RequiredArgsConstructor
public class TokenService {
 private final JwtTokenProvider jwt;
 private final RefreshSessionRepository sessions;
 private final RevokedAccessTokenRepository revoked;
 private final SecureRandom random =new SecureRandom();
 private String randomToken(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @Transactional
 public TokenResponse issue(User user,boolean autoLogin){String refresh=randomToken();long seconds=autoLogin?1209600:86400;sessions.save(new RefreshSession(user,hash(refresh),Instant.now().plusSeconds(seconds),autoLogin));return new TokenResponse(user.getId(),jwt.issue(user.getId()),refresh,JwtTokenProvider.ACCESS_TTL,seconds,user.isOnboardingCompleted());}
 @Transactional
 public RefreshResponse refresh(String refresh){RefreshSession session=sessions.findLockedByTokenHash(hash(refresh)).orElseThrow(()->new BusinessException(ErrorCode.INVALID_INPUT));
 if(!session.getExpiresAt().isAfter(Instant.now()))throw new BusinessException(ErrorCode.INVALID_INPUT);
 String next=randomToken();long seconds=session.isAutoLogin()?1209600:86400;session.rotate(hash(next),Instant.now().plusSeconds(seconds));return new RefreshResponse(jwt.issue(session.getUser().getId()),next,JwtTokenProvider.ACCESS_TTL,seconds);}
 @Transactional
 public void logout(String refresh,String access){sessions.deleteByTokenHash(hash(refresh));if(access!=null&&!access.isBlank())try{Instant exp=jwt.expiry(access);if(exp.isAfter(Instant.now()))revoked.save(new RevokedAccessToken(hash(access),exp));}catch(Exception ignored){} }
 @Transactional public void revokeAll(Long userId){sessions.deleteByUserId(userId);}
 public boolean isRevoked(String token){return revoked.findById(hash(token)).map(e->e.getExpiresAt().isAfter(Instant.now())).orElse(false);}
}
