package gachi.chagok_server.domain.user.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="user_consent_history",indexes=@Index(name="idx_consent_user_type",columnList="user_id,type"))
public class ConsentHistory {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private ConsentType type;
  @Column(nullable=false) private boolean agreed;
  @Column(nullable=false,length=32) private String version;
  @Column(nullable=false) private Instant updatedAt;
  public ConsentHistory(User user, ConsentType type, boolean agreed,String version){this.user=user;this.type=type;this.agreed=agreed;this.version=version;this.updatedAt=Instant.now();}
}
