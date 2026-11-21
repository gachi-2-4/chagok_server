package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.ai.client.AiClientException;
import gachi.chagok_server.domain.ai.client.AiClientException.Kind;
import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Analysis;
import gachi.chagok_server.domain.ai.dto.AiDocumentDtos.Conditions;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.document.entity.AnalysisStep;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.FailReason;
import gachi.chagok_server.domain.document.entity.UploadSource;
import gachi.chagok_server.domain.document.event.DocumentAnalysisRequestedEvent;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.notification.entity.NotificationType;
import gachi.chagok_server.domain.notification.service.NotificationPublisher;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentAnalysisServiceTest {

    private static final Long DOCUMENT_ID = 520L;
    private static final Long USER_ID = 1L;

    @Mock DocumentRepository documents;
    @Mock FileStorage storage;
    @Mock AiServerClient ai;
    @Mock DocumentResultApplier applier;
    @Mock SummaryCardService summaryCards;
    @Mock NotificationPublisher publisher;

    private DocumentAnalysisService service;
    private Document document;
    private final DocumentAnalysisRequestedEvent event = new DocumentAnalysisRequestedEvent(DOCUMENT_ID, USER_ID, null, null);

    private static final byte[] FILE = {1, 2, 3};

    private static Analysis analysis() {
        return new Analysis(DOCUMENT_ID, "LEASE_CONTRACT", 0.9, "CONTRACT", List.of(), "계약서", "요약", List.of(), List.of(),
                new Conditions(List.of()), List.of(), false, null, "원문", 1, null, null, List.of());
    }

    @BeforeEach
    void setUp() {
        // 트랜잭션 관리자는 목으로 대신한다 (콜백만 실행되면 충분)
        service = new DocumentAnalysisService(documents, storage, ai, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                applier, summaryCards, publisher);
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        Box box = new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME);
        document = new Document(user, box, "새 서류", false, null, 1, UploadSource.CAMERA);
        document.attachFiles("documents/1/520/original.pdf", 3L, "thumb");
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        given(documents.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
    }

    private void stubFile() {
        given(storage.get("documents/1/520/original.pdf")).willReturn(FILE);
    }

    @Test
    @DisplayName("분석에 성공하면 결과를 반영하고 쉬운말 요약 카드를 이어서 만든다")
    void success() {
        // given
        stubFile();
        Analysis analysis = analysis();
        given(ai.analyzeDocument(eq(DOCUMENT_ID), eq(USER_ID), eq(FILE), anyString(), any(), any(), any(LocalDate.class))).willReturn(analysis);

        // when
        service.analyze(event);

        // then
        assertThat(document.getAnalysisStep()).isEqualTo(AnalysisStep.READING_TEXT);
        verify(applier).apply(DOCUMENT_ID, analysis);
        verify(summaryCards).generate(DOCUMENT_ID, USER_ID);
    }

    @Test
    @DisplayName("AI가 글자를 인식하지 못하면(422) 다시 시도하지 않고 TEXT_NOT_RECOGNIZED로 실패 처리한다")
    void textNotRecognized() {
        // given
        stubFile();
        given(ai.analyzeDocument(any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.TEXT_NOT_RECOGNIZED, "422"));

        // when
        service.analyze(event);

        // then
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailReason()).isEqualTo(FailReason.TEXT_NOT_RECOGNIZED);
        verify(ai, times(1)).analyzeDocument(any(), any(), any(), any(), any(), any(), any());
        verify(publisher).publish(any(User.class), eq(NotificationType.ANALYSIS_FAILED), anyString(), anyString(), any());
        verify(applier, never()).apply(any(), any());
        verify(summaryCards, never()).generate(any(), any());
    }

    @Test
    @DisplayName("AI 서버 오류는 한 번 더 시도하고, 그래도 실패하면 ANALYSIS_ERROR로 실패 처리한다")
    void serverErrorRetriesOnce() {
        // given
        stubFile();
        given(ai.analyzeDocument(any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.SERVER_ERROR, "500"));

        // when
        service.analyze(event);

        // then
        verify(ai, times(2)).analyzeDocument(any(), any(), any(), any(), any(), any(), any());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailReason()).isEqualTo(FailReason.ANALYSIS_ERROR);
        verify(publisher).publish(any(User.class), eq(NotificationType.ANALYSIS_FAILED), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("시간 초과 뒤 다시 시도해서 성공하면 정상 처리한다")
    void timeoutThenSuccess() {
        // given
        stubFile();
        Analysis analysis = analysis();
        given(ai.analyzeDocument(any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.TIMEOUT, "timeout"))
                .willReturn(analysis);

        // when
        service.analyze(event);

        // then
        verify(applier).apply(DOCUMENT_ID, analysis);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ANALYZING);
    }

    @Test
    @DisplayName("AI 서버가 요청을 거절하면(4xx) 다시 시도하지 않는다")
    void rejectedIsNotRetried() {
        // given
        stubFile();
        given(ai.analyzeDocument(any(), any(), any(), any(), any(), any(), any()))
                .willThrow(new AiClientException(Kind.REJECTED, "400"));

        // when
        service.analyze(event);

        // then
        verify(ai, times(1)).analyzeDocument(any(), any(), any(), any(), any(), any(), any());
        assertThat(document.getFailReason()).isEqualTo(FailReason.ANALYSIS_ERROR);
    }

    @Test
    @DisplayName("저장소에서 파일을 읽지 못해도 서류를 ANALYSIS_ERROR로 실패 처리한다")
    void storageFailure() {
        // given
        given(storage.get(anyString())).willThrow(new IllegalStateException("S3 오류"));

        // when
        service.analyze(event);

        // then
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailReason()).isEqualTo(FailReason.ANALYSIS_ERROR);
        verify(ai, never()).analyzeDocument(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("결과 저장에 실패하면 ANALYSIS_ERROR로 실패 처리하고 요약 카드는 만들지 않는다")
    void applyFailure() {
        // given
        stubFile();
        given(ai.analyzeDocument(any(), any(), any(), any(), any(), any(), any())).willReturn(analysis());
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 오류")).when(applier).apply(any(), any());

        // when
        service.analyze(event);

        // then
        assertThat(document.getFailReason()).isEqualTo(FailReason.ANALYSIS_ERROR);
        verify(summaryCards, never()).generate(any(), any());
    }

    @Test
    @DisplayName("이미 분석이 끝났거나 실패한 서류는 다시 분석하지 않는다")
    void skipsNonAnalyzingDocument() {
        // given
        document.fail(FailReason.ANALYSIS_ERROR);

        // when
        service.analyze(event);

        // then
        verify(ai, never()).analyzeDocument(any(), any(), any(), any(), any(), any(), any());
    }
}
