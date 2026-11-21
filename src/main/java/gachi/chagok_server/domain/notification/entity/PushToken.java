package gachi.chagok_server.domain.notification.entity;
import gachi.chagok_server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @Table(name="push_tokens")
public class PushToken {
 @Id @Column(length=36) private String deviceId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
 @Column(nullable=false,length=512) private String fcmToken;
 @Column(nullable=false,length=12) private String platform;
 private boolean permissionGranted;
 private String appVersion;
 public PushToken(String deviceId,User user){this.deviceId=deviceId;this.user=user;}
 public void update(User user,String token,String platform,boolean permission,String version){this.user=user;this.fcmToken=token;this.platform=platform;this.permissionGranted=permission;this.appVersion=version;}
}
