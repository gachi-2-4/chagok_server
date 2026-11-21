package gachi.chagok_server.domain.document.repository;

import gachi.chagok_server.domain.document.entity.DocumentCheckItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentCheckItemRepository extends JpaRepository<DocumentCheckItem, Long> {

    List<DocumentCheckItem> findByDocumentId(Long documentId);

    Optional<DocumentCheckItem> findByDocumentIdAndCheckKey(Long documentId, String checkKey);

    @Modifying
    @Query("delete from DocumentCheckItem c where c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
