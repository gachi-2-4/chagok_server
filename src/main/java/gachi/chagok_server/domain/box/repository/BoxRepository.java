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

    /** 회원 탈퇴: 사용자의 박스를 모두 지운다 (서류는 먼저 지운 뒤에 부른다). */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from Box b where b.user.id = :userId")
    int deleteAllByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
