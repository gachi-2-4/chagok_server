package gachi.chagok_server.domain.notification.repository;
import gachi.chagok_server.domain.notification.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PushTokenRepository extends JpaRepository<PushToken,String> {
 void deleteByDeviceIdAndUserId(String deviceId,Long userId);
 java.util.List<PushToken> findByUserIdAndPermissionGrantedTrue(Long userId);
 /** 회원 탈퇴: 사용자의 푸시 토큰(기기)을 모두 지운다. */
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true) @org.springframework.data.jpa.repository.Query("delete from PushToken t where t.user.id=:userId") int deleteAllByUserId(@org.springframework.data.repository.query.Param("userId")Long userId);
}
