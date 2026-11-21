package gachi.chagok_server.domain.deadline.schedule;

import gachi.chagok_server.domain.deadline.entity.Deadline;

import java.time.LocalDate;

/**
 * 기한의 회차 하나. 매월 반복 기한은 회차마다 날짜와 완료 여부가 다르다.
 */
public record DeadlineOccurrence(Deadline deadline, LocalDate date, boolean completed, Long reminderId) {
}
