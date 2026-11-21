package gachi.chagok_server.domain.comparison.service;

import gachi.chagok_server.domain.ai.dto.AiComparisonDtos;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.CompareRequest;
import gachi.chagok_server.domain.comparison.dto.ComparisonDtos.Response;
import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.Comparison;
import gachi.chagok_server.domain.comparison.entity.ComparisonStatus;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ComparisonStoreTest {

    private static final Long USER_ID = 1L;

    @Mock ComparisonRepository comparisons;
    @Mock DocumentRepository documents;
    @Mock UserRepository users;
    @Mock NotificationPublisher publisher;

    private ComparisonStore store;
    private User user;
    private Box box;

    @BeforeEach
    void setUp() {
        store = new ComparisonStore(comparisons, documents, users, publisher, new DocumentJson(JsonMapper.builder().build()));
        user = User.phoneUser("01012345678", "hash", "홍길동");
        box = new Box(user, "통신 · 구독", BoxColor.PEACH, BoxIcon.WIFI);
    }

    private Document document(long id, String title, DocCategory category, boolean confirmed) {
        Document document = new Document(user, box, title, false, null, 1, null);
        document.completeAnalysis(title, DocType.ETC, category, "요약", PriorityLevel.HIGH, 1, null, null, false);
        if (confirmed) {
            document.confirm();
        }
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private void stubDocuments(Document base, Document target) {
        given(documents.findByIdAndUserId(base.getId(), USER_ID)).willReturn(Optional.of(base));
        given(documents.findByIdAndUserId(target.getId(), USER_ID)).willReturn(Optional.of(target));
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("계약서와 고지서를 비교하면 비교 ID를 먼저 만든다")
    void reserve() {
        // given
        stubDocuments(document(488, "통신 서비스 계약서", DocCategory.CONTRACT, true), document(533, "11월 통신요금 고지서", DocCategory.BILL, true));
        given(users.findById(USER_ID)).willReturn(Optional.of(user));
        Comparison saved = new Comparison(user, 488L, 533L, CompareType.BILL);
        ReflectionTestUtils.setField(saved, "id", 77L);
        given(comparisons.saveAndFlush(any(Comparison.class))).willReturn(saved);

        // when
        Long id = store.reserve(USER_ID, new CompareRequest(488L, 533L, CompareType.BILL));

        // then
        assertThat(id).isEqualTo(77L);
    }

    @Test
    @DisplayName("같은 서류끼리는 SAME_DOCUMENT")
    void sameDocument() {
        assertError(() -> store.reserve(USER_ID, new CompareRequest(1L, 1L, CompareType.BILL)), ErrorCode.SAME_DOCUMENT);
    }

    @Test
    @DisplayName("내 서류가 아니면 DOCUMENT_NOT_FOUND")
    void notMine() {
        given(documents.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.empty());

        assertError(() -> store.reserve(USER_ID, new CompareRequest(1L, 2L, CompareType.BILL)), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("확인(CONFIRMED)까지 끝나지 않은 서류는 DOCUMENT_NOT_READY")
    void notConfirmed() {
        stubDocuments(document(1, "계약서", DocCategory.CONTRACT, true), document(2, "고지서", DocCategory.BILL, false));

        assertError(() -> store.reserve(USER_ID, new CompareRequest(1L, 2L, CompareType.BILL)), ErrorCode.DOCUMENT_NOT_READY);
    }

    @Test
    @DisplayName("기준은 계약서, 비교 서류는 고지서(BILL)·광고(AD)여야 한다")
    void invalidPair() {
        // 고지서와 비교인데 광고를 고름
        stubDocuments(document(1, "계약서", DocCategory.CONTRACT, true), document(2, "광고", DocCategory.AD, true));
        assertError(() -> store.reserve(USER_ID, new CompareRequest(1L, 2L, CompareType.BILL)), ErrorCode.INVALID_COMPARE_PAIR);
    }

    @Test
    @DisplayName("기준 서류가 계약서가 아니면 INVALID_COMPARE_PAIR")
    void baseNotContract() {
        stubDocuments(document(1, "고지서", DocCategory.BILL, true), document(2, "고지서2", DocCategory.BILL, true));

        assertError(() -> store.reserve(USER_ID, new CompareRequest(1L, 2L, CompareType.BILL)), ErrorCode.INVALID_COMPARE_PAIR);
        verify(comparisons, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AI 결과를 저장하고 읽음 상태의 비교 완료 알림을 남기며, 응답에는 BE 필드를 붙인다")
    void complete() {
        // given
        Document base = document(488, "통신 서비스 계약서", DocCategory.CONTRACT, true);
        Document target = document(533, "11월 통신요금 고지서", DocCategory.BILL, true);
        Comparison comparison = new Comparison(user, 488L, 533L, CompareType.BILL);
        ReflectionTestUtils.setField(comparison, "id", 77L);
        given(comparisons.findByIdAndUserId(77L, USER_ID)).willReturn(Optional.of(comparison));
        given(documents.findById(488L)).willReturn(Optional.of(base));
        given(documents.findById(533L)).willReturn(Optional.of(target));
        AiComparisonDtos.Comparison result = new AiComparisonDtos.Comparison(77L, 488L, 533L, "BILL", "MISMATCH",
                "약속과 다른 금액이 있어요", Map.of("amount", 12000), List.of(Map.of("title", "통신요금 비교")),
                List.of(Map.of("severity", "WARNING")), "추정 결과이며 확인이 필요해요.");

        // when
        Response response = store.complete(USER_ID, 77L, result);

        // then
        assertThat(response.comparisonId()).isEqualTo(77L);
        assertThat(response.status()).isEqualTo(ComparisonStatus.MISMATCH);
        assertThat(response.headline()).isEqualTo("약속과 다른 금액이 있어요");
        assertThat(response.baseDocument().title()).isEqualTo("통신 서비스 계약서");
        assertThat(response.baseDocument().boxName()).isEqualTo("통신 · 구독");
        assertThat(response.targetDocument().documentId()).isEqualTo(533L);
        assertThat(response.highlight()).containsEntry("amount", 12000);
        assertThat(response.disclaimer()).isEqualTo("추정 결과이며 확인이 필요해요.");
        assertThat(response.outdated()).isFalse();
        verify(publisher).publish(eq(user), eq(NotificationType.COMPARISON_COMPLETED), eq("통신 서비스 계약서 비교가 완료됐어요"), any(), any());
    }

    @Test
    @DisplayName("AI가 모르는 상태 값을 주면 비교 항목이 불분명(NEEDS_CHECK)으로 본다")
    void unknownStatus() {
        assertThat(ComparisonStore.statusOf("WHATEVER")).isEqualTo(ComparisonStatus.NEEDS_CHECK);
        assertThat(ComparisonStore.statusOf(null)).isEqualTo(ComparisonStatus.NEEDS_CHECK);
        assertThat(ComparisonStore.statusOf("match")).isEqualTo(ComparisonStatus.MATCH);
    }

    @Test
    @DisplayName("AI 호출 중인(결과가 아직 없는) 비교는 조회할 수 없다")
    void incompleteIsNotFound() {
        // given
        Comparison pending = new Comparison(user, 1L, 2L, CompareType.BILL);
        given(comparisons.findByIdAndUserId(77L, USER_ID)).willReturn(Optional.of(pending));

        // when & then
        assertError(() -> store.get(USER_ID, 77L), ErrorCode.COMPARISON_NOT_FOUND);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 미리 만든 행은 지우되, 완료된 비교는 지우지 않는다")
    void discard() {
        // given
        Comparison pending = new Comparison(user, 1L, 2L, CompareType.BILL);
        Comparison done = new Comparison(user, 1L, 2L, CompareType.BILL);
        done.complete(ComparisonStatus.MATCH, "{}");
        given(comparisons.findById(1L)).willReturn(Optional.of(pending));
        given(comparisons.findById(2L)).willReturn(Optional.of(done));

        // when
        store.discard(1L);
        store.discard(2L);

        // then
        verify(comparisons).delete(pending);
        verify(comparisons, never()).delete(done);
    }
}
