package gachi.chagok_server.domain.home.dto;

import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.notification.entity.NotificationType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 메인 화면 조회 응답 DTO.
 */
public final class HomeDtos {

    private HomeDtos() {
    }

    /** 이번 주 미니 캘린더의 기한 한 줄 */
    public record WeekDeadline(Long deadlineId, DeadlineType type, String label, Long documentId, String documentTitle,
                               long dDay, boolean isCompleted) {
    }

    public record WeekDay(LocalDate date, List<WeekDeadline> deadlines) {
    }

    /** 오늘 이후 완료하지 않은 가장 가까운 기한 */
    public record NextDeadline(Long deadlineId, DeadlineType type, String label, LocalDate date, long dDay,
                               Long documentId, String documentTitle) {
    }

    public record BoxDeadline(Long deadlineId, String label, LocalDate date, long dDay) {
    }

    public record RecentBox(Long boxId, String name, BoxColor color, BoxIcon icon, long documentCount,
                            OffsetDateTime lastOpenedAt, BoxDeadline nearestDeadline) {
    }

    public record NotificationDocument(Long documentId, String title, String boxName) {
    }

    public record RecentNotification(Long notificationId, NotificationType type, String title,
                                     NotificationDocument document, boolean isRead, OffsetDateTime sentAt) {
    }

    public record Response(LocalDate today, List<WeekDay> weekCalendar, NextDeadline nextDeadline,
                           List<RecentBox> recentBoxes, List<RecentNotification> recentNotifications, long unreadCount) {
    }
}
