package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.CalendarEvent;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.document.entity.Document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 문서 분석의 calendarEvents 하나를 기한 하나로 옮긴다. 같은 날짜끼리 짝짓지 않고 하나씩 그대로 옮긴다
 * (같은 날 기한이 둘이면 섞이기 때문). 행동 기한도 calendarEvents에 type=ACTION으로 들어오므로
 * priority.keyDeadline.actionDate로 따로 만들지 않는다.
 */
public final class AiDeadlineMapper {

    private static final int MAX_LABEL_LENGTH = 60;
    private static final Pattern UNTIL = Pattern.compile("UNTIL=(\\d{8})");

    private AiDeadlineMapper() {
    }

    public static Optional<Deadline> toDeadline(Document document, Analysis analysis, CalendarEvent event) {
        if (event.date() == null) {
            return Optional.empty();
        }
        DeadlineType type = typeOf(event.type());
        Recurrence recurrence = event.rrule() != null && event.rrule().contains("FREQ=MONTHLY") ? Recurrence.MONTHLY : null;
        // 금액은 납부 기한에서만 의미가 있다. fieldKey로 항목에서 찾아 채운다 (금액 항목이 아니면 null).
        Long amount = type == DeadlineType.PAYMENT ? DocumentFieldValues.amountOf(analysis, event.fieldKey()).orElse(null) : null;

        return Optional.of(Deadline.fromAi(document.getUser(), document, type, event.fieldKey(), label(event),
                event.date(), recurrence, recurrence == null ? null : untilOf(event.rrule()), amount,
                event.remindBeforeDays()));
    }

    /** 지운 기록과 맞춰 보는 키. 같은 서류를 다시 분석해도 사용자가 지운 AI 기한은 되살리지 않는다. */
    public static String key(DeadlineType type, String fieldKey) {
        return type + "|" + (fieldKey == null ? "" : fieldKey);
    }

    static DeadlineType typeOf(String raw) {
        if (raw == null) {
            return DeadlineType.ETC;
        }
        try {
            return DeadlineType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DeadlineType.ETC;
        }
    }

    private static String label(CalendarEvent event) {
        String title = event.title() == null ? "" : event.title().trim();
        if (title.isEmpty()) {
            return "기한";
        }
        return title.length() > MAX_LABEL_LENGTH ? title.substring(0, MAX_LABEL_LENGTH) : title;
    }

    /** rrule의 UNTIL=20281125 → 2028-11-25. 없으면 계속 반복. */
    static LocalDate untilOf(String rrule) {
        if (rrule == null) {
            return null;
        }
        Matcher matcher = UNTIL.matcher(rrule);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
