package gachi.chagok_server.domain.chat.repository;

import gachi.chagok_server.domain.ai.dto.AiChatDtos.ChatAnswer;
import gachi.chagok_server.domain.box.entity.Box;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.box.repository.BoxRepository;
import gachi.chagok_server.domain.chat.dto.ChatDtos.*;
import gachi.chagok_server.domain.chat.service.ChatStore;
import gachi.chagok_server.domain.chat.service.ChatStore.AskContext;
import gachi.chagok_server.domain.chat.service.ChatStore.ChatLinks;
import gachi.chagok_server.domain.chat.service.ChatStore.MessagePage;
import gachi.chagok_server.domain.chat.support.ChatLinkResolver;
import gachi.chagok_server.domain.chat.support.CitationEnricher;
import gachi.chagok_server.domain.comparison.entity.CompareType;
import gachi.chagok_server.domain.comparison.entity.Comparison;
import gachi.chagok_server.domain.comparison.repository.ComparisonRepository;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.Document;
import gachi.chagok_server.domain.document.repository.DocumentRepository;
import gachi.chagok_server.domain.document.support.DocumentJson;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.config.AppConfig;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 대화 목록 검색·메시지 페이지 나누기·박스 펼치기 쿼리를 실제 JPA 매핑으로 확인한다.
 */
@DataJpaTest
@Import(AppConfig.class)
@TestPropertySource(properties = {"spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
class ChatQueryDataTest {

    @Autowired UserRepository users;
    @Autowired BoxRepository boxes;
    @Autowired DocumentRepository documents;
    @Autowired ChatRepository chats;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatDocumentLinkRepository documentLinks;
    @Autowired ChatBoxLinkRepository boxLinks;
    @Autowired ComparisonRepository comparisons;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private ChatStore store;
    private User user;
    private Box taxBox;
    private Document lease;
    private Document taxBill;

    @BeforeEach
    void setUp() {
        DocumentJson json = new DocumentJson(JsonMapper.builder().build());
        store = new ChatStore(chats, messages, documentLinks, boxLinks, users, new ChatLinkResolver(documents, boxes),
                new CitationEnricher(documents), json);

        user = users.save(User.phoneUser("01012345678", "hash", "홍길동"));
        Box home = boxes.save(new Box(user, "집 · 임대", BoxColor.PEACH, BoxIcon.HOME));
        taxBox = boxes.save(new Box(user, "세금", BoxColor.GRAY, BoxIcon.RECEIPT));
        lease = analyzed(home, "임대차계약서");
        taxBill = analyzed(taxBox, "종합소득세 고지서");
    }

    private Document analyzed(Box box, String title) {
        Document document = documents.save(new Document(user, box, "임시", false, null, 1, null));
        document.completeAnalysis(title, DocType.ETC, DocCategory.CONTRACT, "요약", null, null, null, null, false);
        return document;
    }

    private ChatAnswer answer(String text) {
        return new ChatAnswer(text, "ANSWERED", List.of(Map.of("sourceType", "DOCUMENT", "documentId", lease.getId(), "page", 1)),
                null, List.of("이어서 물어볼 질문"));
    }

    @Test
    @DisplayName("서류와 박스를 넣어 대화를 만들면 박스 안 서류가 viaBoxId와 함께 펼쳐진다")
    void createExpandsBox() {
        // when
        ChatLinks links = store.create(user.getId(), new LinkRequest(List.of(lease.getId()), List.of(taxBox.getId())));

        // then
        assertThat(links.boxes()).extracting(BoxView::name).containsExactly("세금");
        assertThat(links.boxes().get(0).documentCount()).isEqualTo(1);
        assertThat(links.documents()).extracting(DocumentView::documentId).containsExactly(lease.getId(), taxBill.getId());
        assertThat(links.documents()).extracting(DocumentView::viaBoxId).containsExactly(null, taxBox.getId());
        assertThat(links.documents()).extracting(DocumentView::linkStatus).containsOnly(LinkStatus.LINKED);
    }

    @Test
    @DisplayName("서류 변경은 보낸 목록으로 통째로 교체하고 지난 메시지는 그대로 둔다")
    void replaceLinks() {
        // given
        ChatLinks created = store.create(user.getId(), new LinkRequest(List.of(lease.getId()), List.of()));
        store.saveExchange(user.getId(), created.chatId(), "질문", answer("답"));

        // when
        ChatLinks replaced = store.replaceLinks(user.getId(), created.chatId(), new LinkRequest(List.of(), List.of(taxBox.getId())));

        // then
        assertThat(replaced.documents()).extracting(DocumentView::documentId).containsExactly(taxBill.getId());
        assertThat(store.messages(user.getId(), created.chatId(), null, 30).messages()).hasSize(2);
    }

    @Test
    @DisplayName("다른 사용자의 대화는 CHAT_NOT_FOUND")
    void othersChat() {
        // given
        ChatLinks created = store.create(user.getId(), new LinkRequest(null, null));
        User other = users.save(User.phoneUser("01099998888", "hash", "다른 사람"));

        // when & then
        assertThatThrownBy(() -> store.messages(other.getId(), created.chatId(), null, 30))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_NOT_FOUND));
    }

    @Test
    @DisplayName("첫 질문이 대화 제목이 되고(40자까지), 질문과 답변을 함께 저장하며 답변에 근거 표시 문구를 붙인다")
    void saveExchange() {
        // given
        ChatLinks created = store.create(user.getId(), new LinkRequest(List.of(lease.getId()), List.of()));
        String longQuestion = "월".repeat(60);

        // when
        AskResponse response = store.saveExchange(user.getId(), created.chatId(), longQuestion, answer("매월 25일이에요."));
        store.saveExchange(user.getId(), created.chatId(), "두 번째 질문", answer("답2"));

        // then
        assertThat(store.links(user.getId(), created.chatId()).title()).hasSize(40);
        assertThat(response.question().role().name()).isEqualTo("USER");
        assertThat(response.question().citations()).isNull();
        assertThat(response.answer().answerType()).isEqualTo("ANSWERED");
        assertThat(response.answer().followUpQuestions()).containsExactly("이어서 물어볼 질문");
        assertThat(response.answer().citations().get(0)).containsEntry("documentTitle", "임대차계약서")
                .containsEntry("label", "계약서 1페이지");
    }

    @Test
    @DisplayName("메시지는 최신부터 size개를 오래된 순으로 주고, beforeMessageId로 이전 메시지를 이어 받는다")
    void messagePaging() {
        // given
        Long chatId = store.create(user.getId(), new LinkRequest(null, null)).chatId();
        for (int i = 1; i <= 3; i++) {
            store.saveExchange(user.getId(), chatId, "질문" + i, answer("답" + i));
        }

        // when
        MessagePage latest = store.messages(user.getId(), chatId, null, 4);
        MessagePage older = store.messages(user.getId(), chatId, latest.messages().get(0).messageId(), 4);

        // then: 6개 중 최신 4개 → 오래된 순
        assertThat(latest.messages()).extracting(MessageView::content).containsExactly("질문2", "답2", "질문3", "답3");
        assertThat(latest.hasMore()).isTrue();
        assertThat(older.messages()).extracting(MessageView::content).containsExactly("질문1", "답1");
        assertThat(older.hasMore()).isFalse();
    }

    @Test
    @DisplayName("질문을 하지 않은 대화는 목록에서 빠지고, 마지막 메시지 최신순으로 나온다")
    void listOrderAndUntitled() {
        // given
        store.create(user.getId(), new LinkRequest(null, null)); // 질문 없음
        Long first = store.create(user.getId(), new LinkRequest(List.of(lease.getId()), List.of())).chatId();
        Long second = store.create(user.getId(), new LinkRequest(null, null)).chatId();
        store.saveExchange(user.getId(), first, "월세는 언제 내야 하나요?", answer("답"));
        store.saveExchange(user.getId(), second, "관리비가 왜 달라졌나요?", answer("답"));

        // when
        PageResponse<ListItem> page = store.list(user.getId(), null, 0, 20);

        // then
        assertThat(page.content()).extracting(ListItem::chatId).containsExactly(second, first);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content().get(1).documents()).extracting(ListDocument::title).containsExactly("임대차계약서");
    }

    @Test
    @DisplayName("키워드는 대화 제목과 메시지 내용에서 찾고, % _ 는 문자 그대로 찾는다")
    void keywordSearch() {
        // given
        Long rent = store.create(user.getId(), new LinkRequest(null, null)).chatId();
        Long fee = store.create(user.getId(), new LinkRequest(null, null)).chatId();
        Long percent = store.create(user.getId(), new LinkRequest(null, null)).chatId();
        store.saveExchange(user.getId(), rent, "월세는 언제 내야 하나요?", answer("매월 25일이에요."));
        store.saveExchange(user.getId(), fee, "관리비가 왜 달라졌나요?", answer("월세와 별개로 계산돼요."));
        store.saveExchange(user.getId(), percent, "할인율 50% 맞나요?", answer("네."));

        // when & then
        assertThat(ids(store.list(user.getId(), "월세", 0, 20))).containsExactlyInAnyOrder(rent, fee); // 제목 / 답변 내용
        assertThat(ids(store.list(user.getId(), "관리비", 0, 20))).containsExactly(fee);
        assertThat(ids(store.list(user.getId(), "%", 0, 20))).containsExactly(percent);
        assertThat(ids(store.list(user.getId(), "없는말", 0, 20))).isEmpty();
    }

    @Test
    @DisplayName("지운 서류는 대화 목록에 '삭제된 서류'로 남고, 질문할 서류에서는 빠진다")
    void deletedDocument() {
        // given
        Long chatId = store.create(user.getId(), new LinkRequest(List.of(lease.getId(), taxBill.getId()), List.of())).chatId();
        store.saveExchange(user.getId(), chatId, "질문", answer("답"));
        documents.delete(lease);
        documents.flush();

        // when
        PageResponse<ListItem> page = store.list(user.getId(), null, 0, 20);
        AskContext context = store.forQuestion(user.getId(), chatId);

        // then
        assertThat(page.content().get(0).documents()).extracting(ListDocument::deleted).containsExactly(true, false);
        assertThat(page.content().get(0).documents().get(0).title()).isEqualTo("삭제된 서류");
        assertThat(context.linkedDocumentIds()).containsExactly(taxBill.getId());
    }

    @Test
    @DisplayName("연결된 서류가 없으면 NO_LINKED_DOCUMENT, 모두 분석 중이면 DOCUMENT_NOT_READY")
    void forQuestionStates() {
        // given
        Long empty = store.create(user.getId(), new LinkRequest(null, null)).chatId();
        Document analyzing = documents.save(new Document(user, taxBox, "분석 중", false, null, 1, null));
        Long pending = store.create(user.getId(), new LinkRequest(List.of(analyzing.getId()), List.of())).chatId();

        // when & then
        assertThatThrownBy(() -> store.forQuestion(user.getId(), empty))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NO_LINKED_DOCUMENT));
        assertThatThrownBy(() -> store.forQuestion(user.getId(), pending))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_READY));
    }

    @Test
    @DisplayName("박스를 지우면 대화의 박스 연결만 끊긴다")
    void deleteBoxLinks() {
        // given
        Long chatId = store.create(user.getId(), new LinkRequest(null, List.of(taxBox.getId()))).chatId();

        // when
        boxLinks.deleteByBoxId(taxBox.getId());
        boxLinks.flush();

        // then
        assertThat(boxLinks.findByChatIdOrderByIdAsc(chatId)).isEmpty();
        assertThat(chats.findById(chatId)).isPresent();
    }

    @Test
    @DisplayName("서류를 고치면 그 서류를 쓴 비교가 outdated가 되고, 지우면 비교 id를 찾아 지울 수 있다")
    void comparisonQueries() {
        // given
        Comparison used = comparisons.save(new Comparison(user, lease.getId(), taxBill.getId(), CompareType.BILL));
        Comparison other = comparisons.save(new Comparison(user, 9990L, 9991L, CompareType.AD));

        // when
        int marked = comparisons.markOutdatedByDocument(taxBill.getId());
        comparisons.flush();
        List<Long> ids = comparisons.findIdsByDocument(lease.getId());
        comparisons.deleteByIds(ids);
        comparisons.flush();
        // 대량 삭제는 영속성 컨텍스트를 갱신하지 않으므로 비우고 다시 읽는다.
        entityManager.clear();

        // then
        assertThat(marked).isEqualTo(1);
        assertThat(ids).containsExactly(used.getId());
        assertThat(comparisons.findById(used.getId())).isEmpty();
        assertThat(comparisons.findById(other.getId())).isPresent();
    }

    private static List<Long> ids(PageResponse<ListItem> page) {
        return page.content().stream().map(ListItem::chatId).toList();
    }
}
