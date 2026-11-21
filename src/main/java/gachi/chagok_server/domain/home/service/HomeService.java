package gachi.chagok_server.domain.home.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineOccurrence;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.domain.deadline.service.DeadlineQueryService;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.home.dto.HomeDtos.*;
import gachi.chagok_server.domain.notification.entity.Notification;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.global.common.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 메인 화면에 필요한 값을 한 번에 모아 준다: 이번 주 미니 캘린더, 가장 가까운 기한, 최근 연 보관함, 최근 알림.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    static final int RECENT_BOX_LIMIT = 4;

    private final DeadlineQueryService deadlineQuery;
    private final BoxRepository boxes;
    private final DocumentRepository documents;
    private final NotificationRepository notifications;

    public Response home(Long userId) {
        DeadlineSnapshot snapshot = deadlineQuery.snapshot(userId);
        LocalDate today = snapshot.today();

        return new Response(today, weekCalendar(snapshot, today), nextDeadline(snapshot), recentBoxes(userId, snapshot),
                recentNotifications(userId), notifications.countByUserIdAndReadAtIsNull(userId));
    }

    /** 오늘이 든 주의 일요일–토요일 7일. 기한이 없는 날도 빈 배열로 준다. 완료한 기한도 포함한다. */
    private List<WeekDay> weekCalendar(DeadlineSnapshot snapshot, LocalDate today) {
        LocalDate sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate saturday = sunday.plusDays(6);
        Map<LocalDate, List<DeadlineOccurrence>> byDate = snapshot.occurrences(sunday, saturday, true, null, null).stream()
                .collect(Collectors.groupingBy(DeadlineOccurrence::date));

        List<WeekDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = sunday.plusDays(i);
            List<WeekDeadline> deadlines = byDate.getOrDefault(date, List.of()).stream()
                    .map(o -> new WeekDeadline(o.deadline().getId(), o.deadline().getType(), o.deadline().getLabel(),
                            o.deadline().getDocument().getId(), o.deadline().getDocument().getTitle(),
                            KstTime.dDay(today, o.date()), o.completed()))
                    .toList();
            days.add(new WeekDay(date, deadlines));
        }
        return days;
    }

    /** 오늘 이후(오늘 포함) 완료하지 않은 가장 가까운 기한. 이번 주 밖이어도 준다. 없으면 null. */
    private NextDeadline nextDeadline(DeadlineSnapshot snapshot) {
        return snapshot.nextOverall().map(o -> new NextDeadline(o.deadline().getId(), o.deadline().getType(),
                o.deadline().getLabel(), o.date(), KstTime.dDay(snapshot.today(), o.date()),
                o.deadline().getDocument().getId(), o.deadline().getDocument().getTitle())).orElse(null);
    }

    /** 최근에 연 박스 최대 4개 (박스 상세 조회의 lastOpenedAt 순). */
    private List<RecentBox> recentBoxes(Long userId, DeadlineSnapshot snapshot) {
        List<Box> recent = boxes.findTop4ByUserIdAndLastOpenedAtIsNotNullOrderByLastOpenedAtDesc(userId);
        Map<Long, Long> counts = documents.countByBoxIds(recent.stream().map(Box::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getBoxId(), c -> c.getDocumentCount()));
        return recent.stream().limit(RECENT_BOX_LIMIT).map(box -> new RecentBox(box.getId(), box.getName(), box.getColor(),
                box.getIcon(), counts.getOrDefault(box.getId(), 0L), KstTime.toOffset(box.getLastOpenedAt()),
                snapshot.nextOfBox(box.getId()).map(o -> new BoxDeadline(o.deadline().getId(), o.deadline().getLabel(),
                        o.date(), KstTime.dDay(snapshot.today(), o.date()))).orElse(null))).toList();
    }

    private List<RecentNotification> recentNotifications(Long userId) {
        return notifications.findTop3ByUserIdOrderBySentAtDesc(userId).stream().map(this::toRecent).toList();
    }

    private RecentNotification toRecent(Notification n) {
        NotificationDocument document = n.getDocumentId() == null ? null
                : new NotificationDocument(n.getDocumentId(), n.getDocumentTitle(), n.getBoxName());
        return new RecentNotification(n.getId(), n.getType(), n.getTitle(), document, n.getReadAt() != null,
                KstTime.toOffset(n.getSentAt()));
    }
}
