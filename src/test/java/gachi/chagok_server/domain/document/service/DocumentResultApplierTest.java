package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.CalendarEvent;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Conditions;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Field;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.KeyCost;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.PaymentSchedule;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Priority;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.entity.Recurrence;
import gachi.chagok_server.domain.deadline.entity.Reminder;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.FailReason;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import gachi.chagok_server.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentResultApplierTest {

    private static final Long DOCUMENT_ID = 520L;

    @Mock DocumentRepository documents;
    @Mock DocumentAnalysisResultRepository results;
    @Mock DeadlineRepository deadlines;
    @Mock ReminderRepository reminders;
    @Mock ReminderService reminderService;
    @Mock NotificationPublisher publisher;

    private final DocumentJson json = new DocumentJson(JsonMapper.builder().build());
    private DocumentResultApplier applier;
    private Document document;
    private User user;

    @BeforeEach
    void setUp() {
        applier = new DocumentResultApplier(documents, results, deadlines, reminders, reminderService, publisher, json);
        user = User.phoneUser("01012345678", "hash", "홍길동");
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        document = new Document(user, box, "새 서류", false, null, 3, null);
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
    }

    private static Field field(String key, String label, Object value, String valueType, boolean needsReview) {
        return new Field(key, label, label, value, valueType, "근거", null, 0.9, false, needsReview, null, null);
    }

    private static Analysis analysis() {
        return new Analysis(DOCUMENT_ID, "LEASE_CONTRACT", 0.96, "CONTRACT", List.of("서명란 존재"), "주택 임대차 계약서", "요약",
                List.of(), List.of(field("monthlyRent", "월세", 550000, "AMOUNT", false),
                field("contractEndDate", "계약 만료일", "2028-11-30", "DATE", true)),
                new Conditions(List.of(new PaymentSchedule("monthlyRent", "월세 납부", "MONTHLY", 25, null, null, null))),
                List.of(new CalendarEvent("PAYMENT", "monthlyRent", "월세 55만원 납부", LocalDate.of(2026, 12, 25),
                                "FREQ=MONTHLY;BYMONTHDAY=25;UNTIL=20281125", List.of(3, 1)),
                        new CalendarEvent("EXPIRY", "contractEndDate", "임대차 계약 만료", LocalDate.of(2028, 11, 30), null, List.of(60, 30, 7))),
                true, null, "[1페이지]\n원문", 3, Map.of("riskLevel", "LOW"),
                new Priority("HIGH", 86, List.of("이유"), new KeyCost("deposit", "보증금", 10_000_000L), null), List.of());
    }

    @Test
    @DisplayName("분석 결과를 서류에 반영하고, 원문은 따로 저장하며, 기한과 알림을 만들고, 완료 알림을 보낸다")
    void applies() {
        // given
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(deadlines.findByDocumentId(DOCUMENT_ID)).willReturn(List.of());
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.empty());

        // when
        applier.apply(DOCUMENT_ID, analysis());

        // then 서류 정보
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.REVIEW_REQUIRED);
        assertThat(document.getTitle()).isEqualTo("주택 임대차 계약서");
        assertThat(document.getDocType()).isEqualTo(DocType.LEASE_CONTRACT);
        assertThat(document.getDocCategory()).isEqualTo(DocCategory.CONTRACT);
        assertThat(document.getPriorityLevel()).isEqualTo(PriorityLevel.HIGH);
        assertThat(document.getPriorityRank()).isEqualTo(3);
        assertThat(document.isHasCost()).isTrue();
        assertThat(document.getKeyCostLabel()).isEqualTo("보증금");
        assertThat(document.getKeyCostAmount()).isEqualTo(10_000_000L);
        assertThat(document.isNeedsReview()).isTrue();

        // then 분석 결과: 원문은 별도 컬럼, JSON에서는 뺀다
        ArgumentCaptor<DocumentAnalysisResult> saved = ArgumentCaptor.forClass(DocumentAnalysisResult.class);
        verify(results).save(saved.capture());
        assertThat(saved.getValue().getFullText()).isEqualTo("[1페이지]\n원문");
        assertThat(saved.getValue().getAnalysisJson()).doesNotContain("원문").contains("monthlyRent");
        assertThat(saved.getValue().getSearchText()).contains("주택 임대차 계약서").contains("550000");

        // then 기한
        ArgumentCaptor<Deadline> deadline = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlines, times(2)).saveAndFlush(deadline.capture());
        Deadline payment = deadline.getAllValues().get(0);
        assertThat(payment.getType()).isEqualTo(DeadlineType.PAYMENT);
        assertThat(payment.getRecurrence()).isEqualTo(Recurrence.MONTHLY);
        assertThat(payment.getAmount()).isEqualTo(550000L);
        assertThat(deadline.getAllValues().get(1).getType()).isEqualTo(DeadlineType.EXPIRY);
        verify(reminderService, times(2)).autoCreate(any(Deadline.class));

        // then 알림
        verify(publisher).publish(eq(user), eq(NotificationType.ANALYSIS_COMPLETED), any(), any(), any());
    }

    @Test
    @DisplayName("사용자가 이름을 정한 서류는 AI가 준 제목으로 바꾸지 않는다")
    void keepsCustomTitle() {
        // given
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        Document custom = new Document(user, box, "우리 집 계약서", true, null, 3, null);
        ReflectionTestUtils.setField(custom, "id", DOCUMENT_ID);
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(custom));
        given(deadlines.findByDocumentId(DOCUMENT_ID)).willReturn(List.of());
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.empty());

        // when
        applier.apply(DOCUMENT_ID, analysis());

        // then
        assertThat(custom.getTitle()).isEqualTo("우리 집 계약서");
        assertThat(custom.getStatus()).isEqualTo(DocumentStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("다시 분석해도 사용자가 지운 AI 기한은 되살리지 않고, 끈 알림은 다시 켜지 않는다")
    void reanalysisKeepsUserChoices() {
        // given
        Deadline removed = Deadline.fromAi(user, document, DeadlineType.EXPIRY, "contractEndDate", "계약 만료",
                LocalDate.of(2028, 11, 30), null, null, null, List.of());
        removed.markDeleted();
        Deadline reminderOff = Deadline.fromAi(user, document, DeadlineType.PAYMENT, "monthlyRent", "월세 납부",
                LocalDate.of(2026, 12, 25), Recurrence.MONTHLY, null, 550000L, List.of());
        reminderOff.disableReminder();
        ReflectionTestUtils.setField(reminderOff, "id", 71L);
        Deadline userAdded = Deadline.byUser(user, document, DeadlineType.ETC, "내가 추가", LocalDate.of(2027, 1, 1), null, null, null);

        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(deadlines.findByDocumentId(DOCUMENT_ID)).willReturn(List.of(removed, reminderOff, userAdded));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.empty());
        given(reminders.findByDeadlineId(71L)).willReturn(Optional.empty());

        // when
        applier.apply(DOCUMENT_ID, analysis());

        // then: 이전 AI 기한은 지우되(지운 기록은 남김), 사용자가 추가한 기한은 그대로 둔다
        verify(deadlines).delete(reminderOff);
        verify(deadlines, never()).delete(removed);
        verify(deadlines, never()).delete(userAdded);
        // 사용자가 지운 EXPIRY 기한은 새로 만들지 않고, 납부 기한만 다시 만든다
        ArgumentCaptor<Deadline> created = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlines, times(1)).saveAndFlush(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(DeadlineType.PAYMENT);
        assertThat(created.getValue().isReminderDisabled()).isTrue();
    }

    @Test
    @DisplayName("분석 중이 아닌 서류에는 결과를 반영하지 않는다")
    void ignoresWhenNotAnalyzing() {
        // given
        document.fail(FailReason.ANALYSIS_ERROR);
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));

        // when
        applier.apply(DOCUMENT_ID, analysis());

        // then
        verifyNoInteractions(results, deadlines, publisher);
    }

    @Test
    @DisplayName("그 사이 서류가 지워졌으면 아무것도 하지 않는다")
    void ignoresDeletedDocument() {
        // given
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.empty());

        // when
        applier.apply(DOCUMENT_ID, analysis());

        // then
        verifyNoInteractions(results, deadlines, publisher);
    }

    // ---- 복구용 재분석 (사용자가 고친 값 유지) ----

    private static Field edited(String key, Object value, Object aiValue, String valueType) {
        return new Field(key, key, key, value, valueType, "근거", null, 1.0, false, false, true, aiValue);
    }

    /** 사용자가 월세를 60만 원, 계약 만료일을 2029-01-31로, 납부일을 10일로 고친 이전 분석 */
    private Analysis previousEdited() {
        return new Analysis(DOCUMENT_ID, "LEASE_CONTRACT", 0.96, "CONTRACT", List.of(), "주택 임대차 계약서", "요약", List.of(),
                List.of(edited("monthlyRent", 600000, 550000, "AMOUNT"), edited("contractEndDate", "2029-01-31", "2028-11-30", "DATE")),
                new Conditions(List.of(new PaymentSchedule("monthlyRent", "월세 납부", "MONTHLY", 10, null, null, true))),
                List.of(), false, null, null, 3, null, null, List.of());
    }

    @Test
    @DisplayName("복구용 재분석은 고친 항목·납부일·기한·알림 설정을 새 결과에 이어 붙이고, 원래 상태로 되돌리며, 알림은 보내지 않는다")
    void recoveryCarriesOverUserEdits() {
        // given: 이전 분석(고친 값 있음)과 이전 기한·알림
        DocumentAnalysisResult previous = new DocumentAnalysisResult(document);
        previous.replace(json.write(previousEdited()), "원문", "검색");
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(previous));

        Deadline oldPayment = Deadline.fromAi(user, document, DeadlineType.PAYMENT, "monthlyRent", "월세 납부",
                LocalDate.of(2026, 12, 10), Recurrence.MONTHLY, null, 600000L, List.of());
        oldPayment.change("월세 (내가 고침)", null, null, null, null, null, false, false, false);
        ReflectionTestUtils.setField(oldPayment, "id", 71L);
        Deadline oldExpiry = Deadline.fromAi(user, document, DeadlineType.EXPIRY, "contractEndDate", "임대차 계약 만료",
                LocalDate.of(2029, 1, 31), null, null, null, List.of());
        ReflectionTestUtils.setField(oldExpiry, "id", 72L);
        given(deadlines.findByDocumentId(DOCUMENT_ID)).willReturn(List.of(oldPayment, oldExpiry));
        given(reminders.findByDeadlineId(71L)).willReturn(Optional.of(
                new Reminder(oldPayment, LocalTime.of(8, 0), List.of(3), true, null)));
        given(reminders.findByDeadlineId(72L)).willReturn(Optional.empty());
        given(reminderService.autoCreate(any(Deadline.class))).willAnswer(invocation ->
                Optional.of(new Reminder(invocation.getArgument(0), LocalTime.of(9, 0), List.of(1, 0), true, null)));

        // when: AI가 다시 읽은 값은 고치기 전 값이다
        applier.apply(DOCUMENT_ID, analysis(), DocumentStatus.CONFIRMED);

        // then 서류 상태: 확인 완료로 되돌리고 완료 알림은 없다
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.CONFIRMED);
        verifyNoInteractions(publisher);

        // then 분석 결과: 고친 값이 이어 붙고 AI가 새로 읽은 값은 aiValue로 남는다
        Analysis stored = json.readAnalysis(previous.getAnalysisJson());
        Field rent = stored.fields().stream().filter(f -> f.key().equals("monthlyRent")).findFirst().orElseThrow();
        assertThat(((Number) rent.value()).longValue()).isEqualTo(600000L);
        assertThat(rent.userEdited()).isTrue();
        assertThat(((Number) rent.aiValue()).longValue()).isEqualTo(550000L);
        assertThat(stored.fields().stream().filter(f -> f.key().equals("contractEndDate")).findFirst().orElseThrow().value())
                .isEqualTo("2029-01-31");
        assertThat(stored.conditions().paymentSchedules().get(0).dayOfMonth()).isEqualTo(10);
        assertThat(previous.getFullText()).isEqualTo("[1페이지]\n원문");

        // then 기한: 고친 항목·기한 값과 알림 설정이 새 기한에 이어 붙는다
        ArgumentCaptor<Deadline> created = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlines, times(2)).saveAndFlush(created.capture());
        Deadline payment = created.getAllValues().stream().filter(d -> d.getType() == DeadlineType.PAYMENT).findFirst().orElseThrow();
        Deadline expiry = created.getAllValues().stream().filter(d -> d.getType() == DeadlineType.EXPIRY).findFirst().orElseThrow();
        assertThat(payment.getAmount()).isEqualTo(600000L);
        assertThat(payment.getLabel()).isEqualTo("월세 (내가 고침)");
        assertThat(payment.getDate().getDayOfMonth()).isEqualTo(10);
        assertThat(expiry.getDate()).isEqualTo(LocalDate.of(2029, 1, 31));

        ArgumentCaptor<Reminder> reminder = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).schedule(reminder.capture());
        assertThat(reminder.getValue().getAlertTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(reminder.getValue().advanceDayList()).containsExactly(3);
    }

    @Test
    @DisplayName("복구용 재분석에서 항목을 비웠던 만료 기한은 다시 만들지 않는다")
    void recoveryKeepsClearedField() {
        // given
        Analysis cleared = new Analysis(DOCUMENT_ID, "LEASE_CONTRACT", 0.96, "CONTRACT", List.of(), "계약서", "요약", List.of(),
                List.of(edited("contractEndDate", null, "2028-11-30", "DATE")), new Conditions(List.of()),
                List.of(), false, null, null, 3, null, null, List.of());
        DocumentAnalysisResult previous = new DocumentAnalysisResult(document);
        previous.replace(json.write(cleared), "원문", "검색");
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(previous));
        given(deadlines.findByDocumentId(DOCUMENT_ID)).willReturn(List.of());

        // when
        applier.apply(DOCUMENT_ID, analysis(), DocumentStatus.REVIEW_REQUIRED);

        // then: 납부 기한만 만들어지고, 비운 만료 기한은 만들지 않는다
        ArgumentCaptor<Deadline> created = ArgumentCaptor.forClass(Deadline.class);
        verify(deadlines, times(1)).saveAndFlush(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(DeadlineType.PAYMENT);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("일반 분석(서류 종류 변경 등)은 이전에 고친 값을 이어 붙이지 않고 완료 알림을 보낸다")
    void normalReanalysisOverwritesEdits() {
        // given
        DocumentAnalysisResult previous = new DocumentAnalysisResult(document);
        previous.replace(json.write(previousEdited()), "원문", "검색");
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
        given(results.findById(DOCUMENT_ID)).willReturn(Optional.of(previous));
        given(deadlines.findByDocumentId(DOCUMENT_ID)).willReturn(List.of());

        // when
        applier.apply(DOCUMENT_ID, analysis());

        // then
        Analysis stored = json.readAnalysis(previous.getAnalysisJson());
        Field rent = stored.fields().stream().filter(f -> f.key().equals("monthlyRent")).findFirst().orElseThrow();
        assertThat(((Number) rent.value()).longValue()).isEqualTo(550000L);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.REVIEW_REQUIRED);
        verify(publisher).publish(eq(user), eq(NotificationType.ANALYSIS_COMPLETED), any(), any(), any());
    }
}
