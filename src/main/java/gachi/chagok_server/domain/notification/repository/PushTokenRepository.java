package gachi.chagok_server.domain.notification.repository;
import gachi.chagok_server.domain.notification.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PushTokenRepository extends JpaRepository<PushToken,String> {
 void deleteByDeviceIdAndUserId(String deviceId,Long userId);
 java.util.List<PushToken> findByUserIdAndPermissionGrantedTrue(Long userId);
 /** FCM이 만료(UNREGISTERED)라고 알려 준 토큰을 지운다. */
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true) @org.springframework.data.jpa.repository.Query("delete from PushToken t where t.fcmToken=:fcmToken") int deleteByFcmToken(@org.springframework.data.repository.query.Param("fcmToken")String fcmToken);
 /** 같은 FCM 토큰이 다른 기기(다른 사용자 포함)에 등록돼 있으면 지운다 (한 기기에서 계정을 바꿔 로그인한 경우). */
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true) @org.springframework.data.jpa.repository.Query("delete from PushToken t where t.fcmToken=:fcmToken and t.deviceId<>:deviceId") int deleteSameTokenOnOtherDevices(@org.springframework.data.repository.query.Param("fcmToken")String fcmToken,@org.springframework.data.repository.query.Param("deviceId")String deviceId);
 /** 회원 탈퇴: 사용자의 푸시 토큰(기기)을 모두 지운다. */
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true) @org.springframework.data.jpa.repository.Query("delete from PushToken t where t.user.id=:userId") int deleteAllByUserId(@org.springframework.data.repository.query.Param("userId")Long userId);
}
