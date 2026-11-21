package gachi.chagok_server.domain.box.service;

import gachi.chagok_server.domain.box.dto.BoxDtos.Created;
import gachi.chagok_server.domain.box.dto.BoxDtos.CreateRequest;
import gachi.chagok_server.domain.box.dto.BoxDtos.Deleted;
import gachi.chagok_server.domain.box.dto.BoxDtos.ListResponse;
import gachi.chagok_server.domain.box.dto.BoxDtos.Sort;
import gachi.chagok_server.domain.box.dto.BoxDtos.UpdateRequest;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.deadline.schedule.DeadlineSnapshot;
import gachi.chagok_server.domain.deadline.service.DeadlineQueryService;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.service.DocumentDeletionService;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoxServiceTest {

    private static final Long USER_ID = 1L;

    @Mock BoxRepository boxes;
    @Mock UserRepository users;
    @Mock DocumentRepository documents;
    @Mock DeadlineQueryService deadlineQuery;
    @Mock DocumentDeletionService documentDeletion;
    @Mock gachi.chagok_server.domain.chat.repository.ChatBoxLinkRepository chatBoxLinks;
    @InjectMocks BoxService boxService;

    private final User user = User.phoneUser("01012345678", "hash", "홍길동");

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(deadlineQuery.snapshot(USER_ID)).thenReturn(new DeadlineSnapshot(List.of(), Set.of(), Map.of(), LocalDate.of(2026, 11, 18)));
    }

    private static Box box(long id, String name, Instant lastOpenedAt) {
        Box box = new Box(null, name, BoxColor.PEACH, BoxIcon.HOME);
        ReflectionTestUtils.setField(box, "id", id);
        box.open(lastOpenedAt);
        return box;
    }

    @Test
    @DisplayName("박스를 만들면 앞뒤 공백을 지운 이름으로 저장한다")
    void create() {
        // given
        given(users.findById(USER_ID)).willReturn(Optional.of(user));
        given(boxes.countByUserId(USER_ID)).willReturn(5L);
        given(boxes.existsByUserIdAndName(USER_ID, "반려동물")).willReturn(false);
        given(boxes.saveAndFlush(any(Box.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Created created = boxService.create(USER_ID, new CreateRequest("  반려동물 ", BoxColor.GREEN, BoxIcon.PET));

        // then
        assertThat(created.name()).isEqualTo("반려동물");
        assertThat(created.color()).isEqualTo(BoxColor.GREEN);
        assertThat(created.documentCount()).isZero();
        assertThat(created.nearestDeadline()).isNull();
    }

    @Test
    @DisplayName("박스 이름이 비었거나 20자를 넘으면 INVALID_BOX_NAME")
    void createWithInvalidName() {
        // given
        given(users.findById(USER_ID)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> boxService.create(USER_ID, new CreateRequest("   ", BoxColor.GREEN, BoxIcon.PET)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BOX_NAME));
        assertThatThrownBy(() -> boxService.create(USER_ID, new CreateRequest("가".repeat(21), BoxColor.GREEN, BoxIcon.PET)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BOX_NAME));
    }

    @Test
    @DisplayName("같은 이름의 박스가 있으면 BOX_NAME_DUPLICATED")
    void createDuplicated() {
        // given
        given(users.findById(USER_ID)).willReturn(Optional.of(user));
        given(boxes.countByUserId(USER_ID)).willReturn(1L);
        given(boxes.existsByUserIdAndName(USER_ID, "세금")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> boxService.create(USER_ID, new CreateRequest("세금", BoxColor.GRAY, BoxIcon.RECEIPT)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOX_NAME_DUPLICATED));
    }

    @Test
    @DisplayName("박스는 30개까지만 만들 수 있다")
    void createOverLimit() {
        // given
        given(users.findById(USER_ID)).willReturn(Optional.of(user));
        given(boxes.countByUserId(USER_ID)).willReturn(30L);

        // when & then
        assertThatThrownBy(() -> boxService.create(USER_ID, new CreateRequest("새 박스", BoxColor.GRAY, BoxIcon.ETC)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOX_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("가입하면 기본 박스 5개를 만든다")
    void createDefaults() {
        // when
        boxService.createDefaults(user);

        // then
        ArgumentCaptor<Box> captor = ArgumentCaptor.forClass(Box.class);
        verify(boxes, times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Box::getName)
                .containsExactly("집 · 임대", "자동차", "세금", "병원 · 보험", "통신 · 구독");
        assertThat(captor.getAllValues()).extracting(Box::getIcon)
                .containsExactly(BoxIcon.HOME, BoxIcon.CAR, BoxIcon.RECEIPT, BoxIcon.HEART, BoxIcon.WIFI);
    }

    @Test
    @DisplayName("최근 연 순서로 조회하면 한 번도 열지 않은 박스는 뒤로 간다")
    void listRecentOpenedPutsUnopenedLast() {
        // given
        Box unopened = box(1L, "열지 않음", null);
        Box older = box(2L, "예전", Instant.parse("2026-11-10T00:00:00Z"));
        Box newer = box(3L, "최근", Instant.parse("2026-11-17T00:00:00Z"));
        given(boxes.findByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of(unopened, older, newer));
        given(documents.countByBoxIds(any())).willReturn(List.of());

        // when
        ListResponse response = boxService.list(USER_ID, Sort.RECENT_OPENED);

        // then
        assertThat(response.content()).extracting(item -> item.name()).containsExactly("최근", "예전", "열지 않음");
    }

    @Test
    @DisplayName("기본 정렬은 만든 순서다")
    void listCreatedOrder() {
        // given
        given(boxes.findByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of(box(1L, "가", null), box(2L, "나", null)));
        given(documents.countByBoxIds(any())).willReturn(List.of());

        // when
        ListResponse response = boxService.list(USER_ID, Sort.CREATED);

        // then
        assertThat(response.content()).extracting(item -> item.boxId()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("이름을 바꿀 때 내 다른 박스와 같으면 BOX_NAME_DUPLICATED")
    void updateDuplicated() {
        // given
        given(boxes.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.of(box(2L, "자동차", null)));
        given(boxes.existsByUserIdAndNameAndIdNot(USER_ID, "세금", 2L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> boxService.update(USER_ID, 2L, new UpdateRequest("세금", null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOX_NAME_DUPLICATED));
    }

    @Test
    @DisplayName("보낸 필드만 바꾼다")
    void updatePartially() {
        // given
        Box target = box(2L, "자동차", null);
        given(boxes.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.of(target));

        // when
        Created updated = boxService.update(USER_ID, 2L, new UpdateRequest(null, BoxColor.GRAY, null));

        // then
        assertThat(updated.name()).isEqualTo("자동차");
        assertThat(updated.color()).isEqualTo(BoxColor.GRAY);
        assertThat(updated.icon()).isEqualTo(BoxIcon.HOME);
    }

    @Test
    @DisplayName("다른 사용자의 박스는 BOX_NOT_FOUND")
    void findOthersBox() {
        // given
        given(boxes.findByIdAndUserId(9L, USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> boxService.open(USER_ID, 9L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOX_NOT_FOUND));
    }

    @Test
    @DisplayName("박스를 열면 연 시각을 남긴다")
    void openRecordsLastOpenedAt() {
        // given
        Box target = box(1L, "집 · 임대", null);
        given(boxes.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(target));

        // when
        boxService.open(USER_ID, 1L);

        // then
        assertThat(target.getLastOpenedAt()).isNotNull();
    }

    @Test
    @DisplayName("박스를 지우면 안의 서류를 모두 지우고 지운 서류 수를 돌려준다")
    void deleteWithDocuments() {
        // given
        Box target = box(1L, "집 · 임대", null);
        given(boxes.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(target));
        given(documentDeletion.deleteAllInBox(target)).willReturn(3);

        // when
        Deleted deleted = boxService.delete(USER_ID, 1L);

        // then
        assertThat(deleted.deletedDocumentCount()).isEqualTo(3);
        verify(chatBoxLinks).deleteByBoxId(1L);
        verify(boxes).delete(target);
    }
}
