package gachi.chagok_server.domain.comparison.entity;

/** AI 서류 비교가 주는 결과 상태. */
public enum ComparisonStatus {
    /** 약속과 다름 */
    MISMATCH,
    /** 일치 */
    MATCH,
    /** 비교 항목이 불분명 */
    NEEDS_CHECK
}
