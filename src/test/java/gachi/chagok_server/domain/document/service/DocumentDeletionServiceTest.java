package gachi.chagok_server.domain.document.service;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.service.ReminderService;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.repository.DocumentAnalysisResultRepository;
import gachi.chagok_server.domain.document.repository.DocumentCheckItemRepository;
import gachi.chagok_server.domain.document.repository.DocumentPageRepository;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentDeletionServiceTest {

    @Mock DocumentRepository documents;
    @Mock DocumentAnalysisResultRepository results;
    @Mock DocumentPageRepository pages;
    @Mock DocumentCheckItemRepository checkItems;
    @Mock DeadlineRepository deadlines;
    @Mock ReminderService reminderService;
    @Mock NotificationRepository notifications;
    @Mock ComparisonRepository comparisons;
    @Mock StorageCleaner storageCleaner;
    @InjectMocks DocumentDeletionService service;

    private Document document(User user, Box box, long id) {
        Document document = new Document(user, box, "서류" + id, false, null, 1, null);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    @Test
    @DisplayName("회원 탈퇴 때는 서류별 파일과 함께 사용자 폴더 전체를 지운다")
    void deleteAllOfUser() {
        // given
        User user = User.phoneUser("01012345678", "hash", "홍길동");
        ReflectionTestUtils.setField(user, "id", 7L);
        Box box = new Box(user, "집", BoxColor.PEACH, BoxIcon.HOME);
        given(documents.findByUserId(7L)).willReturn(List.of(document(user, box, 1L), document(user, box, 2L)));
        given(comparisons.findIdsByDocument(1L)).willReturn(List.of(77L));

        // when
        int deleted = service.deleteAllOfUser(7L);

        // then
        assertThat(deleted).isEqualTo(2);
        verify(notifications).detachComparisons(List.of(77L));
        verify(comparisons).deleteByIds(List.of(77L));
        verify(storageCleaner).deleteByPrefix("documents/7/1/");
        verify(storageCleaner).deleteByPrefix("documents/7/2/");
        verify(storageCleaner).deleteByPrefix("documents/7/");
    }
}
