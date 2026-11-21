package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSchedule;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSchedule.AlertTarget;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * 보낼 때가 된 기한 알림을 찾아 DEADLINE_DUE 알림을 만들고 푸시를 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderDispatchService {

    /** 서버가 오래 멈췄다 켜져도 한 번에 쏟아 내지 않도록 한 알림당 한 번에 처리할 최대 횟수 */
    private static final int MAX_CATCH_UP = 5;

    private final ReminderRepository reminders;
    private final NotificationPublisher publisher;

    /** @return 보낸 알림 수 */
    @Transactional
    public int dispatchDue(Instant now) {
        int sent = 0;
        for (Reminder reminder : reminders.findDue(now)) {
            for (int i = 0; i < MAX_CATCH_UP && isDue(reminder, now); i++) {
                sent += fire(reminder) ? 1 : 0;
            }
        }
        return sent;
    }

    private boolean isDue(Reminder reminder, Instant now) {
        return reminder.getNextAlertAt() != null && !reminder.getNextAlertAt().isAfter(now);
    }

    /** 알림 하나를 보내고 다음 예약 일시를 다시 계산한다. */
    private boolean fire(Reminder reminder) {
        Deadline deadline = reminder.getDeadline();
        Instant alertAt = reminder.getNextAlertAt();
        Optional<AlertTarget> target = DeadlineSchedule.resolveTarget(deadline, reminder, alertAt);

        target.ifPresent(t -> publish(reminder, t));
        reminder.scheduleNext(DeadlineSchedule.nextAlert(deadline, reminder, alertAt).orElse(null));
        return target.isPresent();
    }

    private void publish(Reminder reminder, AlertTarget target) {
        Deadline deadline = reminder.getDeadline();
        Document document = deadline.getDocument();
        publisher.publish(deadline.getUser(), NotificationType.DEADLINE_DUE, title(deadline, target), message(deadline, target.date()),
                n -> {
                    n.withDocument(document.getId(), document.getTitle(), document.getBox().getName());
                    n.withDeadline(deadline.getId(), deadline.getType().name(), deadline.getLabel(), target.date(),
                            deadline.getAmount(), reminder.getId(), target.advanceDays());
                });
    }

    static String title(Deadline deadline, AlertTarget target) {
        if (target.advanceDays() == 0) {
            return "오늘은 " + deadline.getLabel() + " 날이에요";
        }
        return deadline.getLabel() + "까지 " + target.advanceDays() + "일 남았어요";
    }

    static String message(Deadline deadline, LocalDate date) {
        String base = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일까지 " + deadline.getLabel();
        if (deadline.getAmount() == null) {
            return base + "을(를) 확인해 주세요.";
        }
        return base + " " + NumberFormat.getNumberInstance(Locale.KOREA).format(deadline.getAmount()) + "원을 확인해 주세요.";
    }
}
