package gachi.chagok_server.domain.auth.entity;
import jakarta.persistence.*;import lombok.*;import java.time.Instant;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="verification_grants",indexes=@Index(name="idx_grant_hash",columnList="token_hash",unique=true))
public class VerificationGrant {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash;
 @Column(nullable=false,length=20) private String phoneNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private VerificationPurpose purpose;
 @Column(nullable=false) private Instant expiresAt;
 public VerificationGrant(String hash,String phone,VerificationPurpose purpose,Instant expiry){this.tokenHash=hash;this.phoneNumber=phone;this.purpose=purpose;this.expiresAt=expiry;}
}
