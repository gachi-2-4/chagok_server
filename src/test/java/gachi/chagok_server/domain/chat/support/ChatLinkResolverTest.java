package gachi.chagok_server.domain.chat.support;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.chat.dto.ChatDtos.LinkStatus;
import gachi.chagok_server.domain.chat.support.ChatLinkResolver.Linked;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.FailReason;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatLinkResolverTest {

    private static final Long USER_ID = 1L;

    @Mock DocumentRepository documents;
    @Mock BoxRepository boxes;

    private ChatLinkResolver resolver;
    private User user;
    private Box taxBox;

    @BeforeEach
    void setUp() {
        resolver = new ChatLinkResolver(documents, boxes);
        user = User.phoneUser("01012345678", "hash", "홍길동");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        taxBox = new Box(user, "세금", BoxColor.GRAY, BoxIcon.RECEIPT);
        ReflectionTestUtils.setField(taxBox, "id", 3L);
    }

    private Document document(long id, String title, boolean analyzed) {
        Document document = new Document(user, taxBox, title, false, null, 1, null);
        if (analyzed) {
            document.completeAnalysis(title, null, null, "요약", null, null, null, null, false);
        }
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private static Set<Long> ids(Long... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("직접 넣은 서류와 박스 안 서류를 합치고, 같은 서류는 직접 넣은 쪽이 우선한다")
    void expandMergesAndDeduplicates() {
        // given
        Document direct = document(501, "임대차계약서", true);
        Document inBoxDuplicate = document(501, "임대차계약서", true);
        Document inBox = document(570, "소득세 고지서", true);
        given(documents.findAllById(ids(501L))).willReturn(List.of(direct));
        given(boxes.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.of(taxBox));
        given(documents.findByBoxId(3L)).willReturn(List.of(inBoxDuplicate, inBox));

        // when
        List<Linked> result = resolver.expand(USER_ID, ids(501L), ids(3L));

        // then
        assertThat(result).extracting(Linked::documentId).containsExactly(501L, 570L);
        assertThat(result).extracting(Linked::viaBoxId).containsExactly(null, 3L);
    }

    @Test
    @DisplayName("분석 중 서류는 PENDING, 끝난 서류는 LINKED")
    void linkStatus() {
        // given
        given(documents.findAllById(ids(1L, 2L))).willReturn(List.of(document(1, "분석 중", false), document(2, "완료", true)));

        // when
        List<Linked> result = resolver.expand(USER_ID, ids(1L, 2L), ids());

        // then
        assertThat(result).extracting(Linked::linkStatus).containsExactly(LinkStatus.PENDING, LinkStatus.LINKED);
    }

    @Test
    @DisplayName("분석에 실패한 서류는 빼고, 지워진 서류는 '삭제된 서류'로 표시한다")
    void failedAndDeleted() {
        // given
        Document failed = document(1, "실패", false);
        failed.fail(FailReason.ANALYSIS_ERROR);
        given(documents.findAllById(ids(1L, 9L))).willReturn(List.of(failed));

        // when
        List<Linked> result = resolver.expand(USER_ID, ids(1L, 9L), ids());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).documentId()).isEqualTo(9L);
        assertThat(result.get(0).isDeleted()).isTrue();
        assertThat(result.get(0).title()).isEqualTo("삭제된 서류");
        assertThat(resolver.documentViews(result)).isEmpty();
    }

    @Test
    @DisplayName("지워진 박스는 건너뛴다")
    void deletedBoxIsSkipped() {
        // given
        given(boxes.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThat(resolver.expand(USER_ID, ids(), ids(3L))).isEmpty();
    }

    @Test
    @DisplayName("내 서류가 아니면 DOCUMENT_NOT_FOUND")
    void validateNotMine() {
        given(documents.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.empty());

        assertError(() -> resolver.validate(USER_ID, ids(1L), ids()), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("분석에 실패한 서류는 넣을 수 없다 (409)")
    void validateFailedDocument() {
        // given
        Document failed = document(1, "실패", false);
        failed.fail(FailReason.TEXT_NOT_RECOGNIZED);
        given(documents.findByIdAndUserId(1L, USER_ID)).willReturn(Optional.of(failed));

        // when & then
        assertError(() -> resolver.validate(USER_ID, ids(1L), ids()), ErrorCode.DOCUMENT_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("내 박스가 아니면 BOX_NOT_FOUND")
    void validateNotMyBox() {
        given(boxes.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.empty());

        assertError(() -> resolver.validate(USER_ID, ids(), ids(3L)), ErrorCode.BOX_NOT_FOUND);
    }

    @Test
    @DisplayName("박스를 펼친 서류까지 합쳐 5개를 넘으면 TOO_MANY_DOCUMENTS")
    void validateTooMany() {
        // given
        given(boxes.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.of(taxBox));
        given(documents.findByBoxId(3L)).willReturn(LongStream.rangeClosed(1, 6).mapToObj(i -> document(i, "서류" + i, true)).toList());

        // when & then
        assertError(() -> resolver.validate(USER_ID, ids(), ids(3L)), ErrorCode.TOO_MANY_DOCUMENTS);
    }

    @Test
    @DisplayName("5개까지는 넣을 수 있다")
    void validateFiveIsOk() {
        // given
        given(boxes.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.of(taxBox));
        given(documents.findByBoxId(3L)).willReturn(LongStream.rangeClosed(1, 5).mapToObj(i -> document(i, "서류" + i, true)).toList());

        // when & then
        resolver.validate(USER_ID, ids(), ids(3L));
    }
}
