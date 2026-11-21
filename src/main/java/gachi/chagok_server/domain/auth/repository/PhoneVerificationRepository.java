package gachi.chagok_server.domain.auth.repository;
import gachi.chagok_server.domain.auth.entity.*;
import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PhoneVerification p where p.phoneNumber=:phone and p.purpose=:purpose")
 Optional<PhoneVerification> findLocked(@Param("phone") String phone,@Param("purpose") VerificationPurpose purpose);
}
