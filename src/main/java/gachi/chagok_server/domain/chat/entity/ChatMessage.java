package gachi.chagok_server.domain.chat.entity;

import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;

import java.util.Map;

/**
 * 대화 메시지. AI 답변의 answerType·citations·disclaimer·followUpQuestions는 AI 응답 값을 그대로 저장한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_chat", columnList = "chat_id,id"))
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id")
    private Chat chat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Column(nullable = false, length = 4000)
    private String content;

    /** AI 답변만 */
    @Column(length = 30)
    private String answerType;

    /** AI 답변의 근거 목록(JSON). BE가 서류 이름·표시 문구를 붙인 상태로 저장한다. */
    @Column(length = Length.LONG32)
    private String citationsJson;

    @Column(length = 1000)
    private String disclaimer;

    @Column(length = 1000)
    private String followUpQuestionsJson;

    private ChatMessage(Chat chat, ChatRole role, String content) {
        this.chat = chat;
        this.role = role;
        this.content = content;
    }

    public static ChatMessage question(Chat chat, String content) {
        return new ChatMessage(chat, ChatRole.USER, content);
    }

    public static ChatMessage answer(Chat chat, String content, String answerType, String citationsJson,
                                     String disclaimer, String followUpQuestionsJson) {
        ChatMessage message = new ChatMessage(chat, ChatRole.ASSISTANT, content);
        message.answerType = answerType;
        message.citationsJson = citationsJson;
        message.disclaimer = disclaimer;
        message.followUpQuestionsJson = followUpQuestionsJson;
        return message;
    }
}
