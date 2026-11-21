package gachi.chagok_server.domain.auth.entity;
import jakarta.persistence.*;import lombok.*;import java.time.Instant;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @Table(name="revoked_access_tokens")
public class RevokedAccessToken {
 @Id @Column(length=64) private String tokenHash;
 @Column(nullable=false) private Instant expiresAt;
 public RevokedAccessToken(String hash,Instant exp){this.tokenHash=hash;this.expiresAt=exp;}
}
