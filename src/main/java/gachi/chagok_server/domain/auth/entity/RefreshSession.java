package gachi.chagok_server.domain.auth.entity;
import gachi.chagok_server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@Table(name = "refresh_sessions", indexes = @Index(name = "idx_refresh_token_hash", columnList = "token_hash", unique = true))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable=false) private boolean autoLogin = true;

    public RefreshSession(User user, String tokenHash, Instant expiresAt, boolean autoLogin) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt; this.autoLogin = autoLogin;
    }
    public void rotate(String hash, Instant expiration) { tokenHash = hash; expiresAt = expiration; }
}
