package gachi.chagok_server.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대화에 직접 넣은 서류. 서류가 지워져도 연결은 남겨 지난 대화 목록에 "삭제된 서류"로 보이게 하므로
 * 서류 ID만 들고 있다(외래 키 없음).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_documents", uniqueConstraints = @UniqueConstraint(name = "uk_chat_documents", columnNames = {"chat_id", "documentId"}))
public class ChatDocumentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id")
    private Chat chat;

    @Column(nullable = false)
    private Long documentId;

    public ChatDocumentLink(Chat chat, Long documentId) {
        this.chat = chat;
        this.documentId = documentId;
    }
}
