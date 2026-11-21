package gachi.chagok_server.domain.auth.repository;
import gachi.chagok_server.domain.auth.entity.VerificationGrant;
import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface VerificationGrantRepository extends JpaRepository<VerificationGrant,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from VerificationGrant v where v.tokenHash=:hash")
 Optional<VerificationGrant> findLocked(@Param("hash") String hash);
}
