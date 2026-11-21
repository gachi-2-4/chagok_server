package gachi.chagok_server.domain.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 분석에 실패한 서류(FAILED)는 목록에 보이지 않고 24시간 뒤 자동으로 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCleanupScheduler {

    static final Duration FAILED_RETENTION = Duration.ofHours(24);

    private final DocumentDeletionService deletion;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void deleteExpiredFailedDocuments() {
        try {
            int deleted = deletion.deleteFailedBefore(Instant.now().minus(FAILED_RETENTION));
            if (deleted > 0) {
                log.info("분석 실패 서류 {}건 삭제", deleted);
            }
        } catch (RuntimeException e) {
            log.error("분석 실패 서류 정리 중 오류", e);
        }
    }
}
