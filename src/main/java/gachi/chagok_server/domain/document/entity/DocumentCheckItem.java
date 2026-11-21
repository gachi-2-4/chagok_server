package gachi.chagok_server.domain.document.entity;

import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 정리본 '체크' 항목의 완료 상태. 사용자가 바꾼 값은 요약 카드를 다시 만들어도 checkKey 기준으로 유지한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "document_check_items", uniqueConstraints = @UniqueConstraint(name = "uk_document_check_items", columnNames = {"document_id", "check_key"}))
public class DocumentCheckItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "check_key", nullable = false, length = 60)
    private String checkKey;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(nullable = false)
    private boolean done;

    public DocumentCheckItem(Document document, String checkKey, String text, boolean done) {
        this.document = document;
        this.checkKey = checkKey;
        this.text = text;
        this.done = done;
    }

    public void changeText(String text) {
        this.text = text;
    }

    public void markDone(boolean done) {
        this.done = done;
    }
}
