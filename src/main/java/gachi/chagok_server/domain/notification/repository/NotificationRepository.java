package gachi.chagok_server.domain.notification.repository;
import gachi.chagok_server.domain.notification.entity.Notification;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
public interface NotificationRepository extends JpaRepository<Notification,Long>{
 Optional<Notification> findByIdAndUserId(Long id,Long userId);
 Page<Notification> findByUserId(Long userId,Pageable pageable);
 Page<Notification> findByUserIdAndReadAtIsNull(Long userId,Pageable pageable);
 Page<Notification> findByUserIdAndCompletedAtIsNotNull(Long userId,Pageable pageable);
 long countByUserIdAndReadAtIsNull(Long userId);
 @Modifying @Query("update Notification n set n.readAt=:now where n.user.id=:userId and n.readAt is null") int readAll(@Param("userId")Long userId,@Param("now")Instant now);
 long deleteBySentAtBefore(Instant cutoff);
}
