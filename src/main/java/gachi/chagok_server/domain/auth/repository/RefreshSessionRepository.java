package gachi.chagok_server.domain.auth.repository;
import gachi.chagok_server.domain.auth.entity.RefreshSession;
import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RefreshSession r where r.tokenHash=:hash") Optional<RefreshSession> findLockedByTokenHash(@Param("hash") String hash);
 Optional<RefreshSession> findByTokenHash(String tokenHash);
 void deleteByTokenHash(String hash);
 void deleteByUserId(Long userId);
}
