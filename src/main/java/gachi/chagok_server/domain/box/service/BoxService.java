package gachi.chagok_server.domain.box.service;

import gachi.chagok_server.domain.box.dto.BoxDtos.*;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineOccurrence;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.domain.deadline.service.DeadlineQueryService;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.service.DocumentDeletionService;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoxService {

    static final int MAX_BOXES = 30;
    static final int MAX_NAME_LENGTH = 20;

    /** 가입하면 만들어 두는 기본 박스 */
    private record DefaultBox(String name, BoxColor color, BoxIcon icon) {
    }

    private static final List<DefaultBox> DEFAULT_BOXES = List.of(
            new DefaultBox("집 · 임대", BoxColor.PEACH, BoxIcon.HOME),
            new DefaultBox("자동차", BoxColor.GREEN, BoxIcon.CAR),
            new DefaultBox("세금", BoxColor.GRAY, BoxIcon.RECEIPT),
            new DefaultBox("병원 · 보험", BoxColor.GREEN, BoxIcon.HEART),
            new DefaultBox("통신 · 구독", BoxColor.PEACH, BoxIcon.WIFI));

    private final BoxRepository boxes;
    private final UserRepository users;
    private final DocumentRepository documents;
    private final DeadlineQueryService deadlineQuery;
    private final DocumentDeletionService documentDeletion;

    /** 가입한 사용자에게 기본 박스 5개를 만든다. */
    @Transactional
    public void createDefaults(User user) {
        DEFAULT_BOXES.forEach(box -> boxes.save(new Box(user, box.name(), box.color(), box.icon())));
    }

    public ListResponse list(Long userId, Sort sort) {
        List<Box> all = boxes.findByUserIdOrderByIdAsc(userId);
        Stats stats = stats(userId, all);

        Comparator<Box> order = sort == Sort.RECENT_OPENED
                // 최근에 연 순서. 한 번도 열지 않은 박스는 뒤로 보낸다.
                ? Comparator.comparing(Box::getLastOpenedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(Box::getId);

        List<Item> content = all.stream()
                .sorted(order)
                .map(box -> new Item(box.getId(), box.getName(), box.getColor(), box.getIcon(),
                        stats.count(box.getId()), stats.nearest(box.getId()), KstTime.toOffset(box.getLastOpenedAt())))
                .toList();
        return new ListResponse(content);
    }

    @Transactional
    public Created create(Long userId, CreateRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String name = validName(request.name());
        if (boxes.countByUserId(userId) >= MAX_BOXES) {
            throw new BusinessException(ErrorCode.BOX_LIMIT_EXCEEDED);
        }
        if (boxes.existsByUserIdAndName(userId, name)) {
            throw new BusinessException(ErrorCode.BOX_NAME_DUPLICATED);
        }
        Box box = boxes.saveAndFlush(new Box(user, name, request.color(), request.icon()));
        return toCreated(box, userId);
    }

    /** 박스 상세 조회. 부를 때마다 연 기록(lastOpenedAt)을 남긴다. */
    @Transactional
    public Detail open(Long userId, Long boxId) {
        Box box = find(userId, boxId);
        box.open(Instant.now());
        Stats stats = stats(userId, List.of(box));
        return new Detail(box.getId(), box.getName(), box.getColor(), box.getIcon(), stats.count(boxId),
                stats.nearest(boxId), KstTime.toOffset(box.getLastOpenedAt()), KstTime.toOffset(box.getCreatedAt()));
    }

    @Transactional
    public Created update(Long userId, Long boxId, UpdateRequest request) {
        Box box = find(userId, boxId);
        String name = null;
        if (request.name() != null) {
            name = validName(request.name());
            if (boxes.existsByUserIdAndNameAndIdNot(userId, name, boxId)) {
                throw new BusinessException(ErrorCode.BOX_NAME_DUPLICATED);
            }
        }
        box.change(name, request.color(), request.icon());
        boxes.flush();
        return toCreated(box, userId);
    }

    /** 박스와 박스 안 서류를 모두 지운다. 되돌릴 수 없다. */
    @Transactional
    public Deleted delete(Long userId, Long boxId) {
        Box box = find(userId, boxId);
        int deleted = documentDeletion.deleteAllInBox(box);
        boxes.delete(box);
        return new Deleted(boxId, deleted);
    }

    /** 다른 도메인(서류 업로드·이동)이 쓰는 내 박스 조회. 내 박스가 아니면 404. */
    public Box find(Long userId, Long boxId) {
        return boxes.findByIdAndUserId(boxId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOX_NOT_FOUND));
    }

    private String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_BOX_NAME);
        }
        return name;
    }

    private Created toCreated(Box box, Long userId) {
        Stats stats = stats(userId, List.of(box));
        return new Created(box.getId(), box.getName(), box.getColor(), box.getIcon(), stats.count(box.getId()),
                stats.nearest(box.getId()), KstTime.toOffset(box.getCreatedAt()));
    }

    private Stats stats(Long userId, List<Box> target) {
        List<Long> ids = target.stream().map(Box::getId).toList();
        Map<Long, Long> counts = documents.countByBoxIds(ids).stream()
                .collect(Collectors.toMap(c -> c.getBoxId(), c -> c.getDocumentCount()));
        return new Stats(counts, deadlineQuery.snapshot(userId));
    }

    /** 박스별 서류 수와 가장 가까운 기한 */
    private record Stats(Map<Long, Long> counts, DeadlineSnapshot deadlines) {

        long count(Long boxId) {
            return counts.getOrDefault(boxId, 0L);
        }

        NearestDeadline nearest(Long boxId) {
            return deadlines.nextOfBox(boxId)
                    .map((DeadlineOccurrence o) -> new NearestDeadline(o.date(), KstTime.dDay(deadlines.today(), o.date())))
                    .orElse(null);
        }
    }
}
