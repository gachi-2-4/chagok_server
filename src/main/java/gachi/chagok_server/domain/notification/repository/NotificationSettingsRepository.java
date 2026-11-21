package gachi.chagok_server.domain.notification.repository;
import gachi.chagok_server.domain.notification.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings,Long>{}
