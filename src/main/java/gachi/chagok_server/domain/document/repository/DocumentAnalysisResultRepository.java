package gachi.chagok_server.domain.document.repository;

import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAnalysisResultRepository extends JpaRepository<DocumentAnalysisResult, Long> {
}
