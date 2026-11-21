package gachi.chagok_server.domain.user.repository;
import gachi.chagok_server.domain.user.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
