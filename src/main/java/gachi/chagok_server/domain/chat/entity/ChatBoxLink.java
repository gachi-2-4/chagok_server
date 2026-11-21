package gachi.chagok_server.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대화에 통째로 넣은 박스. 박스 안의 서류는 질문할 때마다 현재 상태로 펼친다
 * (나중에 박스에 추가된 서류도 들어가고, 지운 서류는 빠진다). 박스를 지우면 이 연결도 지운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_boxes", uniqueConstraints = @UniqueConstraint(name = "uk_chat_boxes", columnNames = {"chat_id", "boxId"}))
public class ChatBoxLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id")
    private Chat chat;

    @Column(nullable = false)
    private Long boxId;

    public ChatBoxLink(Chat chat, Long boxId) {
        this.chat = chat;
        this.boxId = boxId;
    }
}
