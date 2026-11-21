package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Conditions;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.document.support.DeadlineFieldSync.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineFieldSyncTest {

    private static Field field(String key, Object value, String valueType) {
        return new Field(key, key, key, value, valueType, null, null, 1.0, false, false, true, null);
    }

    private static Analysis analysis(List<Field> fields, List<PaymentSchedule> schedules) {
        return new Analysis(1L, "LEASE_CONTRACT", 0.9, "CONTRACT", List.of(), "계약서", "요약", List.of(), fields,
                new Conditions(schedules), List.of(), false, null, null, 1, null, null, List.of());
    }

    private static Deadline deadline(DeadlineType type, String fieldKey, LocalDate date, Recurrence recurrence) {
        return Deadline.fromAi(null, null, type, fieldKey, "기한", date, recurrence, null, 500000L, List.of());
    }

    @Test
    @DisplayName("만료 날짜 항목을 고치면 만료 기한의 날짜가 바뀐다")
    void expiryDate() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, "contractEndDate", LocalDate.of(2027, 2, 28), null);
        Analysis analysis = analysis(List.of(field("contractEndDate", "2027-11-24", "DATE")), List.of());

        // when
        Result result = DeadlineFieldSync.apply(deadline, analysis, true, false);

        // then
        assertThat(result).isEqualTo(Result.UPDATED);
        assertThat(deadline.getDate()).isEqualTo(LocalDate.of(2027, 11, 24));
    }

    @Test
    @DisplayName("만료 날짜 항목을 비우면 기한이 사라져야 한다")
    void clearedDate() {
        // given
        Deadline deadline = deadline(DeadlineType.EXPIRY, "contractEndDate", LocalDate.of(2027, 2, 28), null);
        Analysis analysis = analysis(List.of(field("contractEndDate", null, "DATE")), List.of());

        // when & then
        assertThat(DeadlineFieldSync.apply(deadline, analysis, true, false)).isEqualTo(Result.REMOVED);
    }

    @Test
    @DisplayName("금액 항목을 고치면 납부 기한의 금액이 바뀐다")
    void paymentAmount() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, "monthlyRent", LocalDate.of(2026, 12, 25), Recurrence.MONTHLY);
        Analysis analysis = analysis(List.of(field("monthlyRent", 600000, "AMOUNT")), List.of());

        // when
        Result result = DeadlineFieldSync.apply(deadline, analysis, true, false);

        // then
        assertThat(result).isEqualTo(Result.UPDATED);
        assertThat(deadline.getAmount()).isEqualTo(600000L);
    }

    @Test
    @DisplayName("매월 납부일을 고치면 납부 기한의 일자가 바뀌고, 없는 날이면 말일로 한다")
    void paymentDay() {
        // given
        Deadline deadline = deadline(DeadlineType.PAYMENT, "monthlyRent", LocalDate.of(2026, 11, 25), Recurrence.MONTHLY);
        Analysis analysis = analysis(List.of(field("monthlyRent", 500000, "AMOUNT")),
                List.of(new PaymentSchedule("monthlyRent", "월세 납부", "MONTHLY", 31, null, null, true)));

        // when
        Result result = DeadlineFieldSync.apply(deadline, analysis, false, true);

        // then: 11월은 30일까지
        assertThat(result).isEqualTo(Result.UPDATED);
        assertThat(deadline.getDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("행동 기한이나 고치지 않은 항목의 기한은 바꾸지 않는다")
    void untouched() {
        // given
        Deadline action = deadline(DeadlineType.ACTION, "contractEndDate", LocalDate.of(2026, 12, 28), null);
        Deadline other = deadline(DeadlineType.EXPIRY, "contractEndDate", LocalDate.of(2027, 2, 28), null);
        Analysis analysis = analysis(List.of(field("contractEndDate", "2027-11-24", "DATE")), List.of());

        // when & then
        assertThat(DeadlineFieldSync.apply(action, analysis, true, false)).isEqualTo(Result.UNCHANGED);
        assertThat(DeadlineFieldSync.apply(other, analysis, false, false)).isEqualTo(Result.UNCHANGED);
        assertThat(action.getDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(other.getDate()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("근거 항목이 없는 기한(사용자가 만든 기한)은 건드리지 않는다")
    void withoutFieldKey() {
        Deadline deadline = deadline(DeadlineType.ETC, null, LocalDate.of(2027, 1, 1), null);

        assertThat(DeadlineFieldSync.apply(deadline, analysis(List.of(), List.of()), true, true)).isEqualTo(Result.UNCHANGED);
    }
}
