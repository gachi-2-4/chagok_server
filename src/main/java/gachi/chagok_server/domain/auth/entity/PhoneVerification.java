package gachi.chagok_server.domain.auth.entity;
import jakarta.persistence.*;import lombok.*;import java.time.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="phone_verifications",uniqueConstraints=@UniqueConstraint(columnNames={"phone_number","purpose"}))
public class PhoneVerification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="phone_number",nullable=false,length=20) private String phoneNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private VerificationPurpose purpose;
 @Column(nullable=false,length=64) private String codeHash;
 @Column(nullable=false) private Instant expiresAt;
 @Column(nullable=false) private Instant lastSentAt;
 @Column(nullable=false) private LocalDate sentDate;
 @Column(nullable=false) private int sentToday;
 @Column(nullable=false) private int failedAttempts;
 @Column(nullable=false) private boolean verified;
 public PhoneVerification(String phone,VerificationPurpose purpose,String hash,Instant now){this.phoneNumber=phone;this.purpose=purpose;resend(hash,now);}
 public void resend(String hash,Instant now){LocalDate date=now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();sentToday=date.equals(sentDate)?sentToday+1:1;sentDate=date;codeHash=hash;expiresAt=now.plusSeconds(180);lastSentAt=now;failedAttempts=0;verified=false;}
 public void failed(){failedAttempts++;}
 public void markVerified(){verified=true;}
}
