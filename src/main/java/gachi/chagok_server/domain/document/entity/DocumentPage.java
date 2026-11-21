package gachi.chagok_server.domain.document.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서류의 페이지 이미지. PDF도 업로드할 때 페이지별 이미지로 바꿔 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "document_pages", uniqueConstraints = @UniqueConstraint(name = "uk_document_pages", columnNames = {"document_id", "page_no"}))
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    /** 1부터 시작 */
    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(nullable = false, length = 300)
    private String imageKey;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    public DocumentPage(Document document, int pageNo, String imageKey, int width, int height) {
        this.document = document;
        this.pageNo = pageNo;
        this.imageKey = imageKey;
        this.width = width;
        this.height = height;
    }
}
