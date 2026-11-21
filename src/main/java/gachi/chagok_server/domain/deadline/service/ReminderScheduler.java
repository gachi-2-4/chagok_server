package gachi.chagok_server.domain.deadline.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 10분마다 보낼 기한 알림을 찾아 발송한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderDispatchService dispatchService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            int sent = dispatchService.dispatchDue(Instant.now());
            if (sent > 0) {
                log.info("기한 알림 {}건 발송", sent);
            }
        } catch (RuntimeException e) {
            log.error("기한 알림 발송 중 오류", e);
        }
    }
}
