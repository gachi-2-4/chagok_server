package gachi.chagok_server.domain.comparison.entity;

/** 비교 종류. */
public enum CompareType {
    /** 고지서와 비교: 계약 금액과 실제 청구 금액 → 초과 추정액 */
    BILL,
    /** 광고와 비교: 광고에서 약속한 조건과 계약서의 실제 조건 */
    AD
}
