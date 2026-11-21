package gachi.chagok_server.domain.document.entity;

/** 분석 실패 이유. */
public enum FailReason {
    /** AI가 글자를 인식하지 못함 (AI 422) */
    TEXT_NOT_RECOGNIZED,
    /** AI 서버 오류·시간 초과 (한 번 더 시도한 뒤) */
    ANALYSIS_ERROR
}
