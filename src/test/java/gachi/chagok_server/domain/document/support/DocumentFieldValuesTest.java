package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFieldValuesTest {

    private static void assertInvalid(String valueType, Object value) {
        assertThatThrownBy(() -> DocumentFieldValues.validate(valueType, value))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FIELD_VALUE));
    }

    @Test
    @DisplayName("금액은 0 이상 정수만 받는다")
    void amount() {
        assertThat(DocumentFieldValues.validate("AMOUNT", 500000)).isEqualTo(500000L);
        assertThat(DocumentFieldValues.validate("AMOUNT", 0)).isEqualTo(0L);
        assertInvalid("AMOUNT", -1);
        assertInvalid("AMOUNT", 1.5);
        assertInvalid("AMOUNT", "500000");
    }

    @Test
    @DisplayName("날짜는 YYYY-MM-DD만 받는다")
    void date() {
        assertThat(DocumentFieldValues.validate("DATE", "2027-11-24")).isEqualTo("2027-11-24");
        assertInvalid("DATE", "2027/11/24");
        assertInvalid("DATE", "2027-02-30");
    }

    @Test
    @DisplayName("텍스트는 200자까지 받는다")
    void text() {
        assertThat(DocumentFieldValues.validate("TEXT", "잔여 기간 월세의 10%")).isEqualTo("잔여 기간 월세의 10%");
        assertInvalid("TEXT", "가".repeat(201));
        assertInvalid("TEXT", 123);
    }

    @Test
    @DisplayName("위약금은 문장이나 AI가 준 객체 모양을 받는다")
    void penalty() {
        Map<String, Object> penalty = Map.of("description", "10% 위약금", "rate", 0.1);

        assertThat(DocumentFieldValues.validate("PENALTY", penalty)).isEqualTo(penalty);
        assertThat(DocumentFieldValues.validate("PENALTY", "잔여 월세의 10%")).isEqualTo("잔여 월세의 10%");
    }

    @Test
    @DisplayName("null은 값을 비운다는 뜻이라 그대로 통과한다")
    void nullClearsValue() {
        assertThat(DocumentFieldValues.validate("AMOUNT", null)).isNull();
        assertThat(DocumentFieldValues.validate("DATE", null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"500000,500000", "'500,000',500000", "50만,500000", "'1.5억',150000000", "3천,3000", "'200,000원',200000"})
    @DisplayName("금액으로 읽히는 검색어는 원 단위 숫자로 바꾼다")
    void amountKeyword(String keyword, long expected) {
        assertThat(DocumentFieldValues.parseAmountKeyword(keyword)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"보험", "임대차계약서", "0", "만"})
    @DisplayName("금액이 아닌 검색어는 비어 있다")
    void notAmountKeyword(String keyword) {
        assertThat(DocumentFieldValues.parseAmountKeyword(keyword)).isEmpty();
    }
}
