package gachi.chagok_server.domain.deadline.schedule;

import gachi.chagok_server.domain.deadline.entity.Deadline;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 한 사용자의 기한 전체를 한 번 읽어 두고, 박스·서류·홈 화면이 필요로 하는 값을 계산한다.
 * 한 요청 안에서 쿼리를 한 번으로 줄이기 위해 만들었다.
 */
public class DeadlineSnapshot {

    /** 반복 기한에서 완료한 회차를 건너뛰며 찾을 최대 횟수 */
    private static final int MAX_SKIP = 36;

    private static final Comparator<DeadlineOccurrence> NEAREST_FIRST = Comparator
            .comparing(DeadlineOccurrence::date)
            .thenComparing(o -> o.deadline().getId());

    private final List<Deadline> deadlines;
    private final Set<String> completedKeys;
    private final Map<Long, Long> reminderIds;
    private final LocalDate today;

    public DeadlineSnapshot(List<Deadline> deadlines, Set<String> completedKeys, Map<Long, Long> reminderIds, LocalDate today) {
        this.deadlines = deadlines;
        this.completedKeys = completedKeys;
        this.reminderIds = reminderIds;
        this.today = today;
    }

    public static String completionKey(Long deadlineId, LocalDate date) {
        return deadlineId + ":" + date;
    }

    public LocalDate today() {
        return today;
    }

    /** 기간 안의 모든 회차를 날짜순으로 돌려준다. */
    public List<DeadlineOccurrence> occurrences(LocalDate from, LocalDate to, boolean includeCompleted, Long boxId, Long documentId) {
        return deadlines.stream()
                .filter(d -> boxId == null || d.getDocument().getBox().getId().equals(boxId))
                .filter(d -> documentId == null || d.getDocument().getId().equals(documentId))
                .flatMap(d -> DeadlineSchedule.occurrences(d, from, to).stream().map(date -> occurrence(d, date)))
                .filter(o -> includeCompleted || !o.completed())
                .sorted(NEAREST_FIRST)
                .toList();
    }

    /** 오늘 이후(오늘 포함) 완료하지 않은 가장 가까운 기한 */
    public Optional<DeadlineOccurrence> nextOverall() {
        return nearest(null, null);
    }

    public Optional<DeadlineOccurrence> nextOfDocument(Long documentId) {
        return nearest(null, documentId);
    }

    public Optional<DeadlineOccurrence> nextOfBox(Long boxId) {
        return nearest(boxId, null);
    }

    public DeadlineOccurrence occurrence(Deadline deadline, LocalDate date) {
        return new DeadlineOccurrence(deadline, date,
                completedKeys.contains(completionKey(deadline.getId(), date)), reminderIds.get(deadline.getId()));
    }

    private Optional<DeadlineOccurrence> nearest(Long boxId, Long documentId) {
        return deadlines.stream()
                .filter(d -> boxId == null || d.getDocument().getBox().getId().equals(boxId))
                .filter(d -> documentId == null || d.getDocument().getId().equals(documentId))
                .map(this::firstIncompleteOnOrAfterToday)
                .flatMap(Optional::stream)
                .min(NEAREST_FIRST);
    }

    private Optional<DeadlineOccurrence> firstIncompleteOnOrAfterToday(Deadline deadline) {
        LocalDate base = today;
        for (int i = 0; i < MAX_SKIP; i++) {
            Optional<LocalDate> next = DeadlineSchedule.nextOnOrAfter(deadline, base);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            DeadlineOccurrence occurrence = occurrence(deadline, next.get());
            if (!occurrence.completed()) {
                return Optional.of(occurrence);
            }
            base = next.get().plusDays(1);
        }
        return Optional.empty();
    }
}
