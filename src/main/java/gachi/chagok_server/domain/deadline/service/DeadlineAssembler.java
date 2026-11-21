package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.*;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSchedule;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.global.common.KstTime;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 기한·기한 알림 엔티티를 응답 DTO로 바꾼다.
 */
@Component
public class DeadlineAssembler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public ReminderSummary summary(Reminder reminder) {
        if (reminder == null) {
            return null;
        }
        return new ReminderSummary(reminder.getId(), reminder.getAlertTime().format(TIME), reminder.advanceDayList());
    }

    /** 기한 추가·수정 응답 */
    public Response response(Deadline deadline, Reminder reminder, Set<String> completedKeys) {
        LocalDate today = KstTime.today();
        LocalDate date = DeadlineSchedule.displayDate(deadline, today);
        return new Response(deadline.getId(), deadline.getDocument().getId(), deadline.getDocument().getTitle(),
                deadline.getType(), deadline.getLabel(), date, deadline.getRecurrence(), deadline.getEndDate(),
                deadline.getAmount(), KstTime.dDay(today, date), deadline.getSource(), deadline.isUserEdited(),
                completedKeys.contains(DeadlineSnapshot.completionKey(deadline.getId(), date)),
                summary(reminder), KstTime.toOffset(deadline.getCreatedAt()));
    }

    /** 서류 상세의 deadlines[] 한 줄 */
    public DocumentDeadline documentDeadline(Deadline deadline, Reminder reminder, Set<String> completedKeys) {
        LocalDate today = KstTime.today();
        LocalDate date = DeadlineSchedule.displayDate(deadline, today);
        return new DocumentDeadline(deadline.getId(), deadline.getType(), deadline.getFieldKey(), deadline.getLabel(), date,
                deadline.getRecurrence(), deadline.getEndDate(), deadline.getAmount(), KstTime.dDay(today, date),
                deadline.suggestedAdvanceDayList(), deadline.getSource(), deadline.isUserEdited(),
                completedKeys.contains(DeadlineSnapshot.completionKey(deadline.getId(), date)), summary(reminder));
    }

    /** 기한 알림 등록·수정 응답 */
    public ReminderResponse reminderResponse(Reminder reminder) {
        Deadline deadline = reminder.getDeadline();
        LocalDate date = DeadlineSchedule.displayDate(deadline, KstTime.today());
        ReminderDeadline target = new ReminderDeadline(deadline.getId(), deadline.getType(), deadline.getLabel(), date,
                deadline.getRecurrence(), deadline.getAmount(),
                new DocumentRef(deadline.getDocument().getId(), deadline.getDocument().getTitle()));
        return new ReminderResponse(reminder.getId(), target, reminder.getAlertTime().format(TIME), reminder.advanceDayList(),
                deadline.isRecurring() && reminder.isRepeatMonthly(), deadline.getEndDate(),
                KstTime.toOffset(reminder.getNextAlertAt()), KstTime.toOffset(reminder.getCreatedAt()));
    }
}
