package gachi.chagok_server.domain.document.repository;

import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    @EntityGraph(attributePaths = "box")
    Optional<Document> findByIdAndUserId(Long id, Long userId);

    List<Document> findByBoxId(Long boxId);

    /** 박스별 서류 수 (분석 실패 서류는 뺀다) */
    @Query("select d.box.id as boxId, count(d) as documentCount from Document d "
            + "where d.box.id in :boxIds and d.status <> :excluded group by d.box.id")
    List<BoxDocumentCount> countByBoxIds(@Param("boxIds") Collection<Long> boxIds, @Param("excluded") DocumentStatus excluded);

    default List<BoxDocumentCount> countByBoxIds(Collection<Long> boxIds) {
        return boxIds.isEmpty() ? List.of() : countByBoxIds(boxIds, DocumentStatus.FAILED);
    }

    long countByBoxIdAndStatusNot(Long boxId, DocumentStatus status);

    /** 분석에 실패한 지 오래된 서류 (24시간 뒤 자동 삭제 대상) */
    List<Document> findByStatusAndUpdatedAtBefore(DocumentStatus status, Instant cutoff);

    interface BoxDocumentCount {
        Long getBoxId();

        long getDocumentCount();
    }
}
