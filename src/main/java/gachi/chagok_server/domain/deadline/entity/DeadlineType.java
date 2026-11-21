package gachi.chagok_server.domain.deadline.entity;

/** 기한 종류. */
public enum DeadlineType {
    /** 납부 (매월 반복 가능) */
    PAYMENT,
    /** 만료 */
    EXPIRY,
    /** 그 전에 해야 할 행동 */
    ACTION,
    ETC
}
