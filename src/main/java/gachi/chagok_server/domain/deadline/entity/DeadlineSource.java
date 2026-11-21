package gachi.chagok_server.domain.deadline.entity;

/** 기한 출처. */
public enum DeadlineSource {
    /** AI 문서 분석에서 뽑은 기한 (사용자가 고치면 userEdited=true) */
    AI,
    /** 사용자가 캘린더에서 추가한 기한 */
    USER
}
