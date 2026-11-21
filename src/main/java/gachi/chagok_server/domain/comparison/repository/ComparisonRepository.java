package gachi.chagok_server.domain.comparison.repository;

import gachi.chagok_server.domain.comparison.entity.Comparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComparisonRepository extends JpaRepository<Comparison, Long> {

    Optional<Comparison> findByIdAndUserId(Long id, Long userId);

    /** 서류 내용을 고치면 그 서류를 쓴 비교 결과는 "다시 비교" 안내 대상이 된다. */
    @Modifying
    @Query("update Comparison c set c.outdated = true where c.baseDocumentId = :documentId or c.targetDocumentId = :documentId")
    int markOutdatedByDocument(@Param("documentId") Long documentId);

    @Query("select c.id from Comparison c where c.baseDocumentId = :documentId or c.targetDocumentId = :documentId")
    List<Long> findIdsByDocument(@Param("documentId") Long documentId);

    @Modifying
    @Query("delete from Comparison c where c.id in :ids")
    void deleteByIds(@Param("ids") List<Long> ids);

    /** 회원 탈퇴: 사용자의 비교 결과를 모두 지운다 (AI 호출 중에 만들어 둔 빈 행 포함). */
    @Modifying(flushAutomatically = true)
    @Query("delete from Comparison c where c.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
