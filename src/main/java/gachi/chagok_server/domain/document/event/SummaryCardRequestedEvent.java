package gachi.chagok_server.domain.document.event;

/**
 * 쉬운말 요약 카드를 (다시) 만들어야 할 때 발행한다 (서류 내용·기한을 고친 뒤).
 * 트랜잭션이 커밋된 뒤 비동기로 AI 서버를 부른다.
 */
public record SummaryCardRequestedEvent(Long documentId, Long userId) {
}
