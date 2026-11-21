package gachi.chagok_server.domain.notification.repository;
import gachi.chagok_server.domain.notification.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PushTokenRepository extends JpaRepository<PushToken,String> {
 void deleteByDeviceIdAndUserId(String deviceId,Long userId);
}
