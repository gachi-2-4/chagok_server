package gachi.chagok_server.domain.document.entity;

/** 서류 상태. FAILED는 목록에 보이지 않고 24시간 뒤 삭제한다. */
public enum DocumentStatus {
    ANALYZING, REVIEW_REQUIRED, CONFIRMED, FAILED
}
