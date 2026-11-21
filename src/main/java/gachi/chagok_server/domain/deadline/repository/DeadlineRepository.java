package gachi.chagok_server.domain.deadline.repository;

import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

    Set<DocumentStatus> ANALYZED = Set.of(DocumentStatus.REVIEW_REQUIRED, DocumentStatus.CONFIRMED);

    @Query("select d from Deadline d join fetch d.document doc join fetch doc.box "
            + "where d.user.id = :userId and d.deleted = false and doc.status in :statuses")
    List<Deadline> findActiveByUserId(@Param("userId") Long userId, @Param("statuses") Collection<DocumentStatus> statuses);

    default List<Deadline> findActiveByUserId(Long userId) {
        return findActiveByUserId(userId, ANALYZED);
    }

    @EntityGraph(attributePaths = {"document", "document.box"})
    Optional<Deadline> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    /** 지운 기록(AI 기한 표시)을 포함한 서류의 모든 기한 */
    List<Deadline> findByDocumentId(Long documentId);

    List<Deadline> findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(Long documentId);

    @Modifying
    @Query("delete from Deadline d where d.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
