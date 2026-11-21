package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.CalendarEvent;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.document.entity.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiDeadlineMapperTest {

    private static final Document DOCUMENT = new Document(null, null, "계약서", false, null, 1, null);

    private static Analysis analysisWith(Field... fields) {
        return new Analysis(1L, "LEASE_CONTRACT", 0.9, "CONTRACT", List.of(), "계약서", "요약", List.of(), List.of(fields),
                null, List.of(), false, null, null, 1, null, null, List.of());
    }

    private static Field amountField(String key, long value) {
        return new Field(key, "월세", "차임", value, "AMOUNT", null, null, 0.95, false, false, null, null);
    }

    @Test
    @DisplayName("매월 납부 일정은 반복·종료일·금액·추천 알림을 옮긴다")
    void monthlyPayment() {
        // given
        CalendarEvent event = new CalendarEvent("PAYMENT", "monthlyRent", "월세 55만원 납부", LocalDate.of(2026, 12, 25),
                "FREQ=MONTHLY;BYMONTHDAY=25;UNTIL=20281125", List.of(3, 1));

        // when
        Optional<Deadline> deadline = AiDeadlineMapper.toDeadline(DOCUMENT, analysisWith(amountField("monthlyRent", 550000)), event);

        // then
        assertThat(deadline).hasValueSatisfying(d -> {
            assertThat(d.getType()).isEqualTo(DeadlineType.PAYMENT);
            assertThat(d.getRecurrence()).isEqualTo(Recurrence.MONTHLY);
            assertThat(d.getEndDate()).isEqualTo(LocalDate.of(2028, 11, 25));
            assertThat(d.getAmount()).isEqualTo(550000L);
            assertThat(d.getSource()).isEqualTo(DeadlineSource.AI);
            assertThat(d.getFieldKey()).isEqualTo("monthlyRent");
            assertThat(d.suggestedAdvanceDayList()).containsExactly(3, 1);
        });
    }

    @Test
    @DisplayName("만료·행동 기한은 금액을 붙이지 않고 반복하지 않는다")
    void expiryAndAction() {
        // given
        CalendarEvent action = new CalendarEvent("ACTION", "contractEndDate", "계약 갱신 여부 통보", LocalDate.of(2028, 9, 30), null, List.of(14, 7, 1));
        CalendarEvent expiry = new CalendarEvent("EXPIRY", "deposit", "임대차 계약 만료", LocalDate.of(2028, 11, 30), null, null);

        // when
        Deadline actionDeadline = AiDeadlineMapper.toDeadline(DOCUMENT, analysisWith(amountField("deposit", 10_000_000)), action).orElseThrow();
        Deadline expiryDeadline = AiDeadlineMapper.toDeadline(DOCUMENT, analysisWith(amountField("deposit", 10_000_000)), expiry).orElseThrow();

        // then
        assertThat(actionDeadline.getType()).isEqualTo(DeadlineType.ACTION);
        assertThat(actionDeadline.getRecurrence()).isNull();
        assertThat(expiryDeadline.getAmount()).isNull();
    }

    @Test
    @DisplayName("날짜가 없는 일정은 기한으로 옮기지 않는다")
    void withoutDate() {
        // given
        CalendarEvent event = new CalendarEvent("PAYMENT", "monthlyRent", "월세", null, null, null);

        // when & then
        assertThat(AiDeadlineMapper.toDeadline(DOCUMENT, analysisWith(), event)).isEmpty();
    }

    @Test
    @DisplayName("알 수 없는 종류는 ETC로, 긴 제목은 60자로 줄인다")
    void unknownTypeAndLongTitle() {
        // given
        CalendarEvent event = new CalendarEvent("SOMETHING", null, "가".repeat(100), LocalDate.of(2026, 12, 1), null, null);

        // when
        Deadline deadline = AiDeadlineMapper.toDeadline(DOCUMENT, analysisWith(), event).orElseThrow();

        // then
        assertThat(deadline.getType()).isEqualTo(DeadlineType.ETC);
        assertThat(deadline.getLabel()).hasSize(60);
    }
}
