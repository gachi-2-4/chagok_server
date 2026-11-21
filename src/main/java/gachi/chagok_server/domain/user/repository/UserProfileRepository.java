package gachi.chagok_server.domain.user.repository;
import gachi.chagok_server.domain.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserProfileRepository extends JpaRepository<UserProfile,Long> {}
