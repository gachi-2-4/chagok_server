package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentReanalysisServiceTest {

    private static final Long USER_ID = 1L;

    @Mock DocumentRepository documents;
    @Mock ApplicationEventPublisher events;

    private DocumentReanalysisService service;
    private User user;
    private Box box;

    @BeforeEach
    void setUp() {
        service = new DocumentReanalysisService(documents, events);
        user = User.phoneUser("01012345678", "hash", "홍길동");
        box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
    }

    private Document analyzedDocument(long id, boolean confirmed) {
        Document document = new Document(user, box, "계약서", false, null, 1, null);
        document.completeAnalysis("계약서", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, "요약", null, null, null, null, false);
        if (confirmed) {
            document.confirm();
        }
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    @Test
    @DisplayName("분석이 끝난 서류를 ANALYZING으로 돌리고, 원래 상태와 서류 종류 힌트를 담아 다시 분석을 요청한다")
    void requestsRecovery() {
        // given
        Document confirmed = analyzedDocument(501L, true);
        given(documents.findByIdAndUserId(501L, USER_ID)).willReturn(Optional.of(confirmed));

        // when
        service.requestRecovery(USER_ID, List.of(501L));

        // then
        assertThat(confirmed.getStatus()).isEqualTo(DocumentStatus.ANALYZING);
        ArgumentCaptor<DocumentAnalysisRequestedEvent> captor = ArgumentCaptor.forClass(DocumentAnalysisRequestedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().documentId()).isEqualTo(501L);
        assertThat(captor.getValue().docTypeHint()).isEqualTo("LEASE_CONTRACT");
        assertThat(captor.getValue().restoreStatus()).isEqualTo(DocumentStatus.CONFIRMED);
        assertThat(captor.getValue().isRecovery()).isTrue();
    }

    @Test
    @DisplayName("분석 중이거나 없는 서류는 건너뛴다")
    void skipsNotAnalyzed() {
        // given
        Document analyzing = new Document(user, box, "분석 중", false, null, 1, null);
        given(documents.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(analyzing));
        given(documents.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.empty());

        // when
        service.requestRecovery(USER_ID, List.of(1L, 2L));

        // then
        verifyNoInteractions(events);
        assertThat(analyzing.getStatus()).isEqualTo(DocumentStatus.ANALYZING);
    }

    @Test
    @DisplayName("확인 전 서류(REVIEW_REQUIRED)는 그 상태로 되돌리도록 요청한다")
    void keepsReviewRequired() {
        // given
        Document review = analyzedDocument(502L, false);
        given(documents.findByIdAndUserId(502L, USER_ID)).willReturn(Optional.of(review));

        // when
        service.requestRecovery(USER_ID, List.of(502L));

        // then
        ArgumentCaptor<DocumentAnalysisRequestedEvent> captor = ArgumentCaptor.forClass(DocumentAnalysisRequestedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().restoreStatus()).isEqualTo(DocumentStatus.REVIEW_REQUIRED);
    }
}
