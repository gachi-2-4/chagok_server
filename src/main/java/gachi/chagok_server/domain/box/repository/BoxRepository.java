package gachi.chagok_server.domain.box.repository;

import gachi.chagok_server.domain.box.entity.Box;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoxRepository extends JpaRepository<Box, Long> {

    Optional<Box> findByIdAndUserId(Long id, Long userId);

    List<Box> findByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    List<Box> findTop4ByUserIdAndLastOpenedAtIsNotNullOrderByLastOpenedAtDesc(Long userId);
}
