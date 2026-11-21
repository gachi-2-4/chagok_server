package gachi.chagok_server.domain.auth.repository;
import gachi.chagok_server.domain.auth.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken,String> {}
