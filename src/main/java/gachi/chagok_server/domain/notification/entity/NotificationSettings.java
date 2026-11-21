package gachi.chagok_server.domain.notification.entity;
import gachi.chagok_server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @Table(name="notification_settings")
public class NotificationSettings {
 @Id private Long userId;
 @MapsId @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
 @Column(nullable=false) private boolean deadlineAlertEnabled=true;
 @Column(nullable=false) private LocalTime defaultAlertTime=LocalTime.of(9,0);
 public NotificationSettings(User user){this.user=user;}
 public void change(Boolean enabled,LocalTime time){if(enabled!=null)deadlineAlertEnabled=enabled;if(time!=null)defaultAlertTime=time;}
}
