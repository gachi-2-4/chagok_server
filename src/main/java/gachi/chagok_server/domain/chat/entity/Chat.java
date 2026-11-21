package gachi.chagok_server.domain.chat.entity;

import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 채팅 대화. 연결된 서류·박스는 {@link ChatDocumentLink}·{@link ChatBoxLink}로 따로 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chats", indexes = @Index(name = "idx_chats_user_last_message", columnList = "user_id,lastMessageAt"))
public class Chat extends BaseTimeEntity {

    static final int MAX_TITLE_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 첫 질문으로 정해진다 (40자까지). 질문하기 전에는 null */
    @Column(length = 100)
    private String title;

    private Instant lastMessageAt;

    public Chat(User user) {
        this.user = user;
    }

    /** 첫 질문이면 대화 제목으로 쓴다 (40자까지 자름). 이미 제목이 있으면 바꾸지 않는다. */
    public void nameFromFirstQuestion(String question) {
        if (title == null) {
            String trimmed = question.trim();
            this.title = trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
        }
    }

    public void touch(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
