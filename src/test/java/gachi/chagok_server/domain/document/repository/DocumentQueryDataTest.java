package gachi.chagok_server.domain.document.repository;

import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.deadline.entity.Deadline;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.deadline.repository.DeadlineRepository;
import gachi.chagok_server.domain.deadline.repository.ReminderRepository;
import gachi.chagok_server.domain.deadline.service.DeadlineQueryService;
import gachi.chagok_server.domain.document.dto.DocumentDtos.ListItem;
import gachi.chagok_server.domain.document.dto.DocumentDtos.Sort;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.entity.DocumentAnalysisResult;
import gachi.chagok_server.domain.document.entity.DocumentStatus;
import gachi.chagok_server.domain.document.entity.FailReason;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.service.DocumentQueryService;
import gachi.chagok_server.domain.document.service.DocumentQueryService.Filter;
import gachi.chagok_server.domain.document.support.DocumentDetailAssembler;
import gachi.chagok_server.domain.notification.repository.NotificationRepository;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.common.KstTime;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.config.AppConfig;
import gachi.chagok_server.global.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 서류 목록의 검색·필터·정렬 쿼리(JPQL·Criteria)를 실제 JPA 매핑으로 확인한다.
 * 서비스는 쿼리에 필요한 저장소만 진짜를 쓰고 나머지는 목으로 대신한다.
 */
@DataJpaTest
@Import(AppConfig.class)
@TestPropertySource(properties = {"spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
class DocumentQueryDataTest {

    @Autowired UserRepository users;
    @Autowired BoxRepository boxes;
    @Autowired DocumentRepository documents;
    @Autowired DocumentAnalysisResultRepository results;
    @Autowired DocumentPageRepository pages;
    @Autowired DeadlineRepository deadlines;
    @Autowired ReminderRepository reminders;
    @Autowired NotificationRepository notifications;

    private DocumentQueryService service;
    private User user;
    private Box home;
    private Box car;

    @BeforeEach
    void setUp() {
        DeadlineQueryService deadlineQuery = new DeadlineQueryService(deadlines, reminders, notifications);
        FileStorage storage = mock(FileStorage.class);
        service = new DocumentQueryService(documents, results, pages, deadlineQuery, mock(DocumentDetailAssembler.class), storage);

        user = users.save(User.phoneUser("01012345678", "hash", "홍길동"));
        home = boxes.save(new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME));
        car = boxes.save(new Box(user, "자동차", BoxColor.GREEN, BoxIcon.CAR));
    }

    private Document analyzed(Box box, String title, DocType type, DocCategory category, PriorityLevel level, int score,
                              String searchText, String fullText) {
        Document document = documents.save(new Document(user, box, "임시", false, null, 1, null));
        document.completeAnalysis(title, type, category, title + " 요약", level, score, null, null, false);
        DocumentAnalysisResult result = new DocumentAnalysisResult(document);
        result.replace("{}", fullText, searchText);
        results.save(result);
        return document;
    }

    private List<String> titles(Filter filter, Sort sort) {
        PageResponse<ListItem> page = service.list(user.getId(), filter, sort, 0, 20);
        return page.content().stream().map(ListItem::title).toList();
    }

    private static Filter filter(Long boxId, String keyword) {
        return new Filter(boxId, keyword, null, null, null, null, null);
    }

    @Test
    @DisplayName("분석에 실패한 서류와 다른 사용자의 서류는 목록에 나오지 않는다")
    void excludesFailedAndOthers() {
        // given
        analyzed(home, "임대차계약서", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, PriorityLevel.HIGH, 80, "", "");
        Document failed = documents.save(new Document(user, home, "실패한 서류", false, null, 1, null));
        failed.fail(FailReason.ANALYSIS_ERROR);
        User other = users.save(User.phoneUser("01099998888", "hash", "다른 사람"));
        Box otherBox = boxes.save(new Box(other, "남의 박스", BoxColor.GRAY, BoxIcon.ETC));
        documents.save(new Document(other, otherBox, "남의 서류", false, null, 1, null));

        // when
        List<String> titles = titles(filter(null, null), Sort.RECENT);

        // then
        assertThat(titles).containsExactly("임대차계약서");
    }

    @Test
    @DisplayName("박스·서류 분류·종류·중요도 필터는 조건에 맞는 서류만 준다")
    void filters() {
        // given
        analyzed(home, "임대차계약서", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, PriorityLevel.HIGH, 80, "", "");
        analyzed(home, "관리비 고지서", DocType.BILL, DocCategory.BILL, PriorityLevel.MEDIUM, 50, "", "");
        analyzed(car, "보험 광고", DocType.ADVERTISEMENT, DocCategory.AD, PriorityLevel.LOW, 10, "", "");

        // when & then
        assertThat(titles(filter(home.getId(), null), Sort.RECENT)).containsExactlyInAnyOrder("임대차계약서", "관리비 고지서");
        assertThat(titles(new Filter(null, null, null, null, null, List.of(DocCategory.AD, DocCategory.INFO), null), Sort.RECENT))
                .containsExactly("보험 광고");
        assertThat(titles(new Filter(null, null, null, null, null, null, DocType.BILL), Sort.RECENT)).containsExactly("관리비 고지서");
        assertThat(titles(new Filter(null, null, PriorityLevel.HIGH, null, null, null, null), Sort.RECENT)).containsExactly("임대차계약서");
    }

    @Test
    @DisplayName("키워드는 서류 이름·항목 값·원문에서 찾고, 금액으로 읽히는 검색어는 금액 항목과도 맞춘다")
    void keywordSearch() {
        // given
        analyzed(home, "임대차계약서", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, PriorityLevel.HIGH, 80,
                "임대차계약서\n월세\n500000\n", "[1페이지] 중도 해지 시 위약금");
        analyzed(car, "자동차 보험", DocType.SERVICE_CONTRACT, DocCategory.CONTRACT, PriorityLevel.MEDIUM, 50,
                "자동차 보험\n보험료\n1200000\n", "[1페이지] 보장 내용");

        // when & then
        assertThat(titles(filter(null, "보험"), Sort.RECENT)).containsExactly("자동차 보험");            // 이름
        assertThat(titles(filter(null, "위약금"), Sort.RECENT)).containsExactly("임대차계약서");            // 원문
        assertThat(titles(filter(null, "50만"), Sort.RECENT)).containsExactly("임대차계약서");              // 금액(50만 = 500000)
        assertThat(titles(filter(null, "500,000"), Sort.RECENT)).containsExactly("임대차계약서");           // 금액(쉼표)
        assertThat(titles(filter(null, "없는말"), Sort.RECENT)).isEmpty();
    }

    @Test
    @DisplayName("검색어의 % _ 는 문자 그대로 찾는다")
    void keywordTreatsWildcardsLiterally() {
        // given
        analyzed(home, "일반 서류", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "일반 서류", "");
        analyzed(home, "할인율 50%", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "할인율 50%", "");

        // when & then
        assertThat(titles(filter(null, "%"), Sort.RECENT)).containsExactly("할인율 50%");
    }

    @Test
    @DisplayName("중요도 정렬은 등급, 같은 등급 안에서는 점수 순이다")
    void sortByPriority() {
        // given
        analyzed(home, "보통 80", DocType.ETC, DocCategory.INFO, PriorityLevel.MEDIUM, 80, "", "");
        analyzed(home, "높음 60", DocType.ETC, DocCategory.INFO, PriorityLevel.HIGH, 60, "", "");
        analyzed(home, "높음 90", DocType.ETC, DocCategory.INFO, PriorityLevel.HIGH, 90, "", "");
        analyzed(home, "낮음 99", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 99, "", "");

        // when & then
        assertThat(titles(filter(null, null), Sort.PRIORITY)).containsExactly("높음 90", "높음 60", "보통 80", "낮음 99");
    }

    @Test
    @DisplayName("이름순 정렬")
    void sortByName() {
        // given
        analyzed(home, "나 서류", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");
        analyzed(home, "가 서류", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");

        // when & then
        assertThat(titles(filter(null, null), Sort.NAME)).containsExactly("가 서류", "나 서류");
    }

    @Test
    @DisplayName("기한 임박 필터와 기한순 정렬은 완료하지 않은 가장 가까운 기한 기준이다")
    void deadlineFilterAndSort() {
        // given
        Document soon = analyzed(home, "곧 만료", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");
        Document later = analyzed(home, "나중 만료", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");
        analyzed(home, "기한 없음", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");
        deadlines.save(Deadline.fromAi(user, soon, DeadlineType.EXPIRY, "k", "만료", KstTime.today().plusDays(5), null, null, null, List.of()));
        deadlines.save(Deadline.fromAi(user, later, DeadlineType.EXPIRY, "k", "만료", KstTime.today().plusDays(90), null, null, null, List.of()));

        // when & then
        assertThat(titles(filter(null, null), Sort.DEADLINE)).containsExactly("곧 만료", "나중 만료", "기한 없음");
        assertThat(titles(new Filter(null, null, null, 30, null, null, null), Sort.RECENT)).containsExactly("곧 만료");
    }

    @Test
    @DisplayName("대표 비용이 있는 서류만 걸러 낸다")
    void hasCostFilter() {
        // given
        Document withCost = documents.save(new Document(user, home, "임시", false, null, 1, null));
        withCost.completeAnalysis("보증금 서류", DocType.LEASE_CONTRACT, DocCategory.CONTRACT, "요약", PriorityLevel.HIGH, 80, "보증금", 10_000_000L, false);
        analyzed(home, "비용 없음", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");

        // when & then
        assertThat(titles(new Filter(null, null, null, null, true, null, null), Sort.RECENT)).containsExactly("보증금 서류");
        PageResponse<ListItem> page = service.list(user.getId(), new Filter(null, null, null, null, true, null, null), Sort.RECENT, 0, 20);
        assertThat(page.content().get(0).priority().keyCost().amount()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("페이지를 나눠 조회하고 다음 페이지 여부를 알려 준다")
    void paging() {
        // given
        for (int i = 0; i < 3; i++) {
            analyzed(home, "서류 " + i, DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");
        }

        // when
        PageResponse<ListItem> first = service.list(user.getId(), filter(null, null), Sort.RECENT, 0, 2);
        PageResponse<ListItem> second = service.list(user.getId(), filter(null, null), Sort.RECENT, 1, 2);

        // then
        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("박스별 서류 수는 분석 실패 서류를 뺀다")
    void countByBoxIds() {
        // given
        analyzed(home, "하나", DocType.ETC, DocCategory.INFO, PriorityLevel.LOW, 1, "", "");
        documents.save(new Document(user, home, "분석 중", false, null, 1, null));
        Document failed = documents.save(new Document(user, home, "실패", false, null, 1, null));
        failed.fail(FailReason.ANALYSIS_ERROR);

        // when
        var counts = documents.countByBoxIds(List.of(home.getId(), car.getId()));

        // then
        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getBoxId()).isEqualTo(home.getId());
        assertThat(counts.get(0).getDocumentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("분석에 실패한 지 24시간이 지난 서류만 정리 대상이다")
    void expiredFailedDocuments() {
        // given
        Document old = documents.saveAndFlush(new Document(user, home, "오래된 실패", false, null, 1, null));
        old.fail(FailReason.ANALYSIS_ERROR);
        documents.flush();

        // when
        List<Document> none = documents.findByStatusAndUpdatedAtBefore(DocumentStatus.FAILED, Instant.now().minus(Duration.ofHours(24)));
        List<Document> expired = documents.findByStatusAndUpdatedAtBefore(DocumentStatus.FAILED, Instant.now().plus(Duration.ofHours(1)));

        // then
        assertThat(none).isEmpty();
        assertThat(expired).hasSize(1);
    }
}
