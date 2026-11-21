package gachi.chagok_server.domain.deadline.event;

import gachi.chagok_server.domain.deadline.entity.DeadlineType;

import java.time.LocalDate;

/**
 * 사용자가 AI 기한을 고쳤을 때 발행한다. 기한이 나온 서류 항목도 같이 고치기 위해
 * 서류 도메인이 받아서 처리한다 (서류·기한 서비스가 서로를 직접 부르지 않도록 이벤트로 연결).
 *
 * @param newDate   바뀐 날짜 (날짜를 바꾸지 않았으면 null)
 * @param newAmount 바뀐 금액 (금액을 바꾸지 않았으면 null)
 */
public record DeadlineEditedEvent(Long userId, Long documentId, String fieldKey, DeadlineType type,
                                  boolean recurring, LocalDate newDate, Long newAmount) {
}
