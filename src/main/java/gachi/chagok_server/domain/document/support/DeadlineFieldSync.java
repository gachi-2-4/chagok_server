package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 사용자가 고친 서류 항목에 맞춰 AI 기한의 날짜·금액을 맞춘다.
 * 만료 날짜·금액·매월 납부일을 바꾸면 기한이 바뀐다 (행동 기한은 항목에서 계산되는 값이라 다시 계산하지 않는다).
 */
public final class DeadlineFieldSync {

    public enum Result {
        /** 바뀐 것이 없다 */
        UNCHANGED,
        /** 날짜나 금액이 바뀌었다 */
        UPDATED,
        /** 항목 값을 비워서 기한이 사라져야 한다 */
        REMOVED
    }

    private DeadlineFieldSync() {
    }

    /**
     * @param fieldChanged    이 기한이 나온 항목(fieldKey)의 값을 고쳤는지
     * @param scheduleChanged 같은 fieldKey의 매월 납부일(dayOfMonth)을 고쳤는지
     */
    public static Result apply(Deadline deadline, Analysis analysis, boolean fieldChanged, boolean scheduleChanged) {
        String key = deadline.getFieldKey();
        if (key == null || (!fieldChanged && !scheduleChanged)) {
            return Result.UNCHANGED;
        }
        Result result = Result.UNCHANGED;

        Field field = DocumentFieldValues.find(analysis, key).orElse(null);
        if (fieldChanged && deadline.getType() == DeadlineType.EXPIRY && field != null && "DATE".equals(field.valueType())) {
            if (field.value() == null) {
                return Result.REMOVED;
            }
            deadline.syncFromField(LocalDate.parse(String.valueOf(field.value())), null);
            result = Result.UPDATED;
        }
        if (fieldChanged && deadline.getType() == DeadlineType.PAYMENT) {
            var amount = DocumentFieldValues.amountOf(analysis, key);
            if (amount.isPresent()) {
                deadline.syncFromField(null, amount.get());
                result = Result.UPDATED;
            }
        }
        if (scheduleChanged && deadline.getType() == DeadlineType.PAYMENT && deadline.isRecurring()
                && analysis.conditions() != null && analysis.conditions().paymentSchedules() != null) {
            int day = analysis.conditions().paymentSchedules().stream()
                    .filter(s -> key.equals(s.fieldKey())).findFirst().map(PaymentSchedule::dayOfMonth).orElse(0);
            if (day > 0) {
                deadline.syncFromField(withDay(deadline.getDate(), day), null);
                result = Result.UPDATED;
            }
        }
        return result;
    }

    /** 그 달에 없는 날이면(예: 31일) 말일로 처리한다. */
    public static LocalDate withDay(LocalDate date, int dayOfMonth) {
        YearMonth month = YearMonth.from(date);
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
