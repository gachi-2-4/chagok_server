package gachi.chagok_server.global.security;
import io.jsonwebtoken.*;import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;import java.nio.charset.StandardCharsets;import java.time.Instant;import java.util.Date;
@Component
public class JwtTokenProvider {
 public static final long ACCESS_TTL=3600;
 private final SecretKey key;
 public JwtTokenProvider(@Value("${auth.jwt.secret}") String secret){byte[] b=secret.getBytes(StandardCharsets.UTF_8);if(b.length<32)throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");key=Keys.hmacShaKeyFor(b);}
 public String issue(Long id){Instant now=Instant.now();return Jwts.builder().subject(id.toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ACCESS_TTL))).signWith(key).compact();}
 public Long userId(String token){return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());}
 public Instant expiry(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration().toInstant();}
}
