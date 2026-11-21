package gachi.chagok_server.domain.deadline.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.Response;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos.UpdateRequest;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.event.DeadlineEditedEvent;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeadlineServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long DEADLINE_ID = 72L;

    @Mock DeadlineRepository deadlines;
    @Mock ReminderRepository reminders;
    @Mock DocumentRepository documents;
    @Mock DeadlineQueryService query;
    @Mock ReminderService reminderService;
    @Mock ApplicationEventPublisher events;

    private DeadlineService service;
    private User user;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new DeadlineService(deadlines, reminders, documents, query, reminderService, new DeadlineAssembler(), events);
        user = User.phoneUser("01012345678", "hash", "홍길동");
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        document = new Document(user, box, "계약서", false, null, 1, null);
        ReflectionTestUtils.setField(document, "id", 520L);
    }

    private Deadline monthlyPayment() {
        Deadline deadline = Deadline.fromAi(user, document, DeadlineType.PAYMENT, "monthlyRent", "월세 납부",
                LocalDate.of(2026, 12, 25), Recurrence.MONTHLY, LocalDate.of(2028, 11, 25), 550000L, List.of());
        ReflectionTestUtils.setField(deadline, "id", DEADLINE_ID);
        given(deadlines.findByIdAndUserIdAndDeletedFalse(DEADLINE_ID, USER_ID)).willReturn(Optional.of(deadline));
        // 검증 오류로 끝나는 테스트에서는 응답을 만들지 않으므로 아래 둘은 쓰이지 않을 수 있다.
        lenient().when(reminders.findByDeadlineId(DEADLINE_ID)).thenReturn(Optional.empty());
        lenient().when(query.completedKeys(USER_ID)).thenReturn(Set.of());
        return deadline;
    }

    private static UpdateRequest request(String label, LocalDate date, Recurrence recurrence, LocalDate endDate, Long amount, String... clear) {
        return new UpdateRequest(label, date, null, recurrence, endDate, amount, Set.of(clear));
    }

    @Test
    @DisplayName("amount를 null로 보내면 금액을 비운다")
    void clearAmount() {
        // given
        Deadline deadline = monthlyPayment();

        // when
        Response response = service.update(USER_ID, DEADLINE_ID, request(null, null, null, null, null, "amount"));

        // then
        assertThat(deadline.getAmount()).isNull();
        assertThat(deadline.isUserEdited()).isTrue();
        assertThat(response.amount()).isNull();
    }

    @Test
    @DisplayName("recurrence를 null로 보내면 반복을 끄고 종료일도 같이 비우며 알림 예약을 다시 계산한다")
    void clearRecurrence() {
        // given
        Deadline deadline = monthlyPayment();

        // when
        Response response = service.update(USER_ID, DEADLINE_ID, request(null, null, null, null, null, "recurrence"));

        // then
        assertThat(deadline.getRecurrence()).isNull();
        assertThat(deadline.getEndDate()).isNull();
        assertThat(deadline.getAmount()).isEqualTo(550000L);
        assertThat(response.recurrence()).isNull();
        verify(reminderService).reschedule(deadline);
    }

    @Test
    @DisplayName("endDate를 null로 보내면 반복은 그대로 두고 종료일만 비운다")
    void clearEndDate() {
        // given
        Deadline deadline = monthlyPayment();

        // when
        service.update(USER_ID, DEADLINE_ID, request(null, null, null, null, null, "endDate"));

        // then
        assertThat(deadline.getRecurrence()).isEqualTo(Recurrence.MONTHLY);
        assertThat(deadline.getEndDate()).isNull();
    }

    @Test
    @DisplayName("보내지 않은 항목은 그대로 두고 보낸 값만 바꾼다")
    void absentFieldsAreKept() {
        // given
        Deadline deadline = monthlyPayment();

        // when
        service.update(USER_ID, DEADLINE_ID, request("관리비 납부", null, null, null, 85000L));

        // then
        assertThat(deadline.getLabel()).isEqualTo("관리비 납부");
        assertThat(deadline.getAmount()).isEqualTo(85000L);
        assertThat(deadline.getRecurrence()).isEqualTo(Recurrence.MONTHLY);
        assertThat(deadline.getEndDate()).isEqualTo(LocalDate.of(2028, 11, 25));
    }

    @Test
    @DisplayName("날짜를 바꾸면 기한이 나온 서류 항목도 같이 고치도록 이벤트를 낸다")
    void dateChangePublishesEvent() {
        // given
        monthlyPayment();

        // when
        service.update(USER_ID, DEADLINE_ID, request(null, LocalDate.of(2026, 12, 10), null, null, null));

        // then
        ArgumentCaptor<DeadlineEditedEvent> captor = ArgumentCaptor.forClass(DeadlineEditedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().fieldKey()).isEqualTo("monthlyRent");
        assertThat(captor.getValue().newDate()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(captor.getValue().recurring()).isTrue();
    }

    @Test
    @DisplayName("이름만 바꾸면 서류 항목을 고치지 않는다")
    void labelChangeDoesNotPublish() {
        // given
        monthlyPayment();

        // when
        service.update(USER_ID, DEADLINE_ID, request("새 이름", null, null, null, null));

        // then
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("이름이 비었거나 30자를 넘거나 금액이 음수거나 종료일이 날짜보다 앞서면 INVALID_DEADLINE")
    void invalidValues() {
        // given
        monthlyPayment();

        // when & then
        for (UpdateRequest invalid : List.of(
                request("  ", null, null, null, null),
                request("가".repeat(31), null, null, null, null),
                request(null, null, null, null, -1L),
                request(null, null, null, LocalDate.of(2026, 1, 1), null))) {
            assertThatThrownBy(() -> service.update(USER_ID, DEADLINE_ID, invalid)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_DEADLINE));
        }
    }

    @Test
    @DisplayName("없는 기한이면 DEADLINE_NOT_FOUND")
    void notFound() {
        // given
        given(deadlines.findByIdAndUserIdAndDeletedFalse(DEADLINE_ID, USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.update(USER_ID, DEADLINE_ID, request(null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DEADLINE_NOT_FOUND));
    }
}
