package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Conditions;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.event.DeadlineEditedEvent;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.FieldEdit;
import gachi.chagok_server.domain.document.dto.DocumentDtos.FieldsRequest;
import gachi.chagok_server.domain.document.dto.DocumentDtos.ScheduleEdit;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.event.SummaryCardRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentDetailAssembler;
import gachi.chagok_server.domain.document.support.DocumentJson;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentFieldServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long DOCUMENT_ID = 501L;

    @Mock DocumentRepository documents;
    @Mock DocumentAnalysisResultRepository results;
    @Mock DeadlineRepository deadlines;
    @Mock ReminderRepository reminders;
    @Mock ReminderService reminderService;
    @Mock ComparisonRepository comparisons;
    @Mock DocumentDetailAssembler assembler;
    @Mock ApplicationEventPublisher events;

    private final DocumentJson json = new DocumentJson(JsonMapper.builder().build());
    private DocumentFieldService service;
    private Document document;
    private DocumentAnalysisResult result;
    private User user;

    private static Field field(String key, Object value, String valueType) {
        return new Field(key, key, key, value, valueType, "근거", null, 0.9, false, false, null, null);
    }

    private Analysis analysis() {
        return new Analysis(DOCUMENT_ID, "LEASE_CONTRACT", 0.9, "CONTRACT", List.of(), "계약서", "요약", List.of(),
                List.of(field("monthlyRent", 550000, "AMOUNT"), field("contractEndDate", "2028-11-30", "DATE"),
                        field("memo", "특약 없음", "TEXT")),
                new Conditions(List.of(new PaymentSchedule("monthlyRent", "월세 납부", "MONTHLY", 25, null, null, null))),
                List.of(), false, null, null, 1, null, null, List.of());
    }

    @BeforeEach
    void setUp() {
        service = new DocumentFieldService(documents, results, deadlines, reminders, reminderService, comparisons, assembler,
                json, events);
        user = User.phoneUser("01012345678", "hash", "홍길동");
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        document = new Document(user, box, "계약서", false, null, 1, null);
        document.completeAnalysis("계약서", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, "요약", null, null, null, null, false);
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        result = new DocumentAnalysisResult(document);
        result.replace(json.write(analysis()), "원문", "검색");
    }

    private void stubFound() {
        given(documents.findByIdAndUserId(DOCUMENT_ID, USER_ID)).willReturn(Optional.of(document));
    }

    private void stubResult() {
        stubFound();
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(result));
    }

    private Deadline deadline(DeadlineType type, String fieldKey, LocalDate date, Recurrence recurrence, Long amount) {
        return Deadline.fromAi(user, document, type, fieldKey, "기한", date, recurrence, null, amount, List.of());
    }

    private Analysis stored() {
        return json.readAnalysis(result.getAnalysisJson());
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("금액을 고치면 고친 표시가 붙고, 납부 기한 금액이 바뀌고, 비교 결과가 outdated가 되고, 요약 카드를 다시 요청한다")
    void editAmount() {
        // given
        stubResult();
        Deadline payment = deadline(DeadlineType.PAYMENT, "monthlyRent", LocalDate.of(2026, 12, 25), Recurrence.MONTHLY, 550000L);
        given(deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(DOCUMENT_ID)).willReturn(List.of(payment));

        // when
        service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, List.of(new FieldEdit("monthlyRent", 600000)), null));

        // then
        Field rent = stored().fields().get(0);
        assertThat(((Number) rent.value()).longValue()).isEqualTo(600000L);
        assertThat(rent.userEdited()).isTrue();
        assertThat(rent.needsReview()).isFalse();
        assertThat(((Number) rent.aiValue()).longValue()).isEqualTo(550000L);
        assertThat(payment.getAmount()).isEqualTo(600000L);
        verify(reminderService).reschedule(payment);
        verify(comparisons).markOutdatedByDocument(DOCUMENT_ID);
        verify(events).publishEvent(new SummaryCardRequestedEvent(DOCUMENT_ID, USER_ID));
        assertThat(result.getSearchText()).contains("600000");
    }

    @Test
    @DisplayName("매월 납부일을 고치면 납부 기한의 일자가 바뀐다")
    void editPaymentDay() {
        // given
        stubResult();
        Deadline payment = deadline(DeadlineType.PAYMENT, "monthlyRent", LocalDate.of(2026, 12, 25), Recurrence.MONTHLY, 550000L);
        given(deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(DOCUMENT_ID)).willReturn(List.of(payment));

        // when
        service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, null, List.of(new ScheduleEdit("monthlyRent", 10))));

        // then
        assertThat(stored().conditions().paymentSchedules().get(0).dayOfMonth()).isEqualTo(10);
        assertThat(stored().conditions().paymentSchedules().get(0).userEdited()).isTrue();
        assertThat(payment.getDate()).isEqualTo(LocalDate.of(2026, 12, 10));
        verify(reminderService).reschedule(payment);
    }

    @Test
    @DisplayName("만료 날짜를 비우면 그 기한과 알림을 지운다")
    void clearExpiryDate() {
        // given
        stubResult();
        Deadline expiry = deadline(DeadlineType.EXPIRY, "contractEndDate", LocalDate.of(2028, 11, 30), null, null);
        ReflectionTestUtils.setField(expiry, "id", 72L);
        Reminder reminder = new Reminder(expiry, LocalTime.of(9, 0), List.of(30), true, null);
        given(deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(DOCUMENT_ID)).willReturn(List.of(expiry));
        given(reminders.findByDeadlineId(72L)).willReturn(Optional.of(reminder));

        // when
        service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, List.of(new FieldEdit("contractEndDate", null)), null));

        // then
        verify(reminders).delete(reminder);
        verify(deadlines).delete(expiry);
    }

    @Test
    @DisplayName("고치지 않은 항목의 기한은 건드리지 않는다")
    void untouchedDeadlines() {
        // given
        stubResult();
        Deadline expiry = deadline(DeadlineType.EXPIRY, "contractEndDate", LocalDate.of(2028, 11, 30), null, null);
        given(deadlines.findByDocumentIdAndDeletedFalseOrderByDateAscIdAsc(DOCUMENT_ID)).willReturn(List.of(expiry));

        // when
        service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, List.of(new FieldEdit("memo", "수정한 메모")), null));

        // then
        assertThat(expiry.getDate()).isEqualTo(LocalDate.of(2028, 11, 30));
        verify(reminderService, never()).reschedule(any());
    }

    @Test
    @DisplayName("없는 항목 key는 UNKNOWN_FIELD_KEY, 형식이 틀린 값은 INVALID_FIELD_VALUE, 납부일 범위를 벗어나면 INVALID_FIELD_VALUE")
    void invalidEdits() {
        // given
        stubResult();

        // when & then
        assertError(() -> service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, List.of(new FieldEdit("nothing", 1)), null)),
                ErrorCode.UNKNOWN_FIELD_KEY);
        assertError(() -> service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, List.of(new FieldEdit("monthlyRent", -5)), null)),
                ErrorCode.INVALID_FIELD_VALUE);
        assertError(() -> service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, null, List.of(new ScheduleEdit("monthlyRent", 32)))),
                ErrorCode.INVALID_FIELD_VALUE);
        assertError(() -> service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(null, null, List.of(new ScheduleEdit("other", 5)))),
                ErrorCode.UNKNOWN_FIELD_KEY);
    }

    @Test
    @DisplayName("서류 종류를 바꾸면 ANALYZING으로 돌리고 종류 힌트로 다시 분석을 요청하며 비교 결과를 outdated로 한다")
    void changeDocType() {
        // given
        stubFound();

        // when
        service.edit(USER_ID, DOCUMENT_ID, new FieldsRequest(DocType.SERVICE_CONTRACT, null, null));

        // then
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ANALYZING);
        ArgumentCaptor<DocumentAnalysisRequestedEvent> captor = ArgumentCaptor.forClass(DocumentAnalysisRequestedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().docTypeHint()).isEqualTo("SERVICE_CONTRACT");
        assertThat(captor.getValue().isRecovery()).isFalse();
        verify(comparisons).markOutdatedByDocument(DOCUMENT_ID);
    }

    @Test
    @DisplayName("서류 종류는 다른 수정과 함께 보낼 수 없다")
    void docTypeWithOtherEdits() {
        stubFound();

        assertError(() -> service.edit(USER_ID, DOCUMENT_ID,
                new FieldsRequest(DocType.BILL, List.of(new FieldEdit("memo", "x")), null)), ErrorCode.INVALID_INPUT);
        verifyNoInteractions(comparisons);
    }

    @Test
    @DisplayName("분석 중이거나 없는 서류는 고칠 수 없다")
    void notEditable() {
        // given
        Document analyzing = new Document(user, document.getBox(), "분석 중", false, null, 1, null);
        given(documents.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(analyzing));
        given(documents.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.empty());

        // when & then
        assertError(() -> service.edit(USER_ID, 1L, new FieldsRequest(null, null, null)), ErrorCode.DOCUMENT_NOT_READY);
        assertError(() -> service.edit(USER_ID, 2L, new FieldsRequest(null, null, null)), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("기한을 고치면 기한이 나온 서류 항목도 같이 고친다 (만료 날짜 → 날짜 항목)")
    void deadlineEditUpdatesDateField() {
        // given
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(result));

        // when
        service.onDeadlineEdited(new DeadlineEditedEvent(USER_ID, DOCUMENT_ID, "contractEndDate", DeadlineType.EXPIRY, false,
                LocalDate.of(2029, 1, 31), null));

        // then
        Field end = stored().fields().get(1);
        assertThat(end.value()).isEqualTo("2029-01-31");
        assertThat(end.userEdited()).isTrue();
        verify(comparisons).markOutdatedByDocument(DOCUMENT_ID);
        verify(events).publishEvent(new SummaryCardRequestedEvent(DOCUMENT_ID, USER_ID));
    }

    @Test
    @DisplayName("매월 납부 기한의 날짜를 고치면 납부일(dayOfMonth)과 금액 항목도 같이 고친다")
    void deadlineEditUpdatesScheduleAndAmount() {
        // given
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(result));

        // when
        service.onDeadlineEdited(new DeadlineEditedEvent(USER_ID, DOCUMENT_ID, "monthlyRent", DeadlineType.PAYMENT, true,
                LocalDate.of(2026, 12, 10), 700000L));

        // then
        assertThat(stored().conditions().paymentSchedules().get(0).dayOfMonth()).isEqualTo(10);
        assertThat(((Number) stored().fields().get(0).value()).longValue()).isEqualTo(700000L);
    }

    @Test
    @DisplayName("고칠 항목이 없는 기한 수정은 서류를 건드리지 않는다")
    void deadlineEditWithoutMatchingField() {
        // given
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(result));
        String before = result.getAnalysisJson();

        // when
        service.onDeadlineEdited(new DeadlineEditedEvent(USER_ID, DOCUMENT_ID, "unknown", DeadlineType.EXPIRY, false,
                LocalDate.of(2029, 1, 31), null));

        // then
        assertThat(result.getAnalysisJson()).isEqualTo(before);
        verifyNoInteractions(comparisons, events);
    }
}
