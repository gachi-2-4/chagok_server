package gachi.chagok_server.domain.user.repository;
import gachi.chagok_server.domain.user.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory,Long> {
 List<ConsentHistory> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
}
