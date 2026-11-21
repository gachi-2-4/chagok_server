package gachi.chagok_server.domain.user.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="user_profiles")
public class UserProfile {
    @Id private Long userId;
    @MapsId @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
    @Column(nullable=false) private String nationality;
    @Column(nullable=false) private String sido;
    @Column(nullable=false) private String sigungu;
    @Column(nullable=false) private String housingType;
    @Column(nullable=false) private String welfareType;
    @Column(nullable=false) private Instant updatedAt;
    public UserProfile(User user) {this.user=user;}
    public void update(String nationality,String sido,String sigungu,String housingType,String welfareType) {
        this.nationality=nationality;this.sido=sido;this.sigungu=sigungu;
        this.housingType=housingType;this.welfareType=welfareType;this.updatedAt=Instant.now();
    }
}
