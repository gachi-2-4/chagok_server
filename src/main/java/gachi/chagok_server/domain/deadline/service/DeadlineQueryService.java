package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.global.common.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 캘린더·박스·서류 목록·홈 화면이 함께 쓰는 기한 조회.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeadlineQueryService {

    private final DeadlineRepository deadlines;
    private final ReminderRepository reminders;
    private final NotificationRepository notifications;

    /** 사용자의 분석이 끝난 서류에 붙은 기한 전체를 읽어 계산 도구로 만든다. */
    public DeadlineSnapshot snapshot(Long userId) {
        List<Deadline> all = deadlines.findActiveByUserId(userId);

        Map<Long, Long> reminderIds = all.isEmpty()
                ? Map.of()
                : reminders.findByDeadlineIdIn(all.stream().map(Deadline::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getDeadline().getId(), Reminder::getId));

        return new DeadlineSnapshot(all, completedKeys(userId), reminderIds, KstTime.today());
    }

    /** 알림에서 완료로 표시한 기한 회차의 키 집합 ({@link DeadlineSnapshot#completionKey}) */
    public Set<String> completedKeys(Long userId) {
        Set<String> completed = new HashSet<>();
        notifications.findCompletedDeadlines(userId).forEach(row ->
                completed.add(DeadlineSnapshot.completionKey(row.getDeadlineId(), row.getDeadlineDate())));
        return completed;
    }
}
