package gachi.chagok_server.global.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 한국 표준시(KST) 기준 날짜·시각 계산 도우미.
 * API 명세는 일시를 KST 오프셋이 붙은 ISO-8601(2026-11-24T09:00:00+09:00)로 준다.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(9);

    private KstTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(OFFSET);
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).toInstant();
    }

    /** 오늘(KST)부터 date까지 남은 일수. 지났으면 음수. */
    public static long dDay(LocalDate date) {
        return dDay(today(), date);
    }

    public static long dDay(LocalDate today, LocalDate date) {
        return ChronoUnit.DAYS.between(today, date);
    }
}
