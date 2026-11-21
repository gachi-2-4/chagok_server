package gachi.chagok_server.domain.user.repository;
import gachi.chagok_server.domain.user.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    java.util.List<SocialAccount> findByUserId(Long userId);
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
    /** 회원 탈퇴: 소셜 연결을 지워, 같은 소셜 계정으로 다시 로그인하면 새 사용자로 가입되게 한다. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from SocialAccount s where s.user.id = :userId")
    int deleteAllByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
