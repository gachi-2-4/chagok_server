package gachi.chagok_server.domain.document.repository;

import gachi.chagok_server.domain.document.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {

    List<DocumentPage> findByDocumentIdOrderByPageNoAsc(Long documentId);

    @Modifying
    @Query("delete from DocumentPage p where p.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
