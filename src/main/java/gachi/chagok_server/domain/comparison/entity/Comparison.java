package gachi.chagok_server.domain.comparison.entity;

import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;

/**
 * 서류 비교 결과. AI는 결과를 저장하지 않으므로 BE가 AI 응답을 그대로 저장한다.
 * 서류는 삭제되면 비교 결과도 같이 지우므로 서류 ID만 들고 있다.
 * AI를 부르기 전에 비교 ID가 필요해서 먼저 빈 행으로 만들고, 결과가 오면 채운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comparisons", indexes = {
        @Index(name = "idx_comparisons_base", columnList = "baseDocumentId"),
        @Index(name = "idx_comparisons_target", columnList = "targetDocumentId")
})
public class Comparison extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 기준 서류 (계약서) */
    @Column(nullable = false)
    private Long baseDocumentId;

    /** 비교한 서류 (고지서·광고) */
    @Column(nullable = false)
    private Long targetDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CompareType compareType;

    /** AI 결과가 오기 전에는 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComparisonStatus status;

    /** AI 서류 비교 응답 전체 (headline·highlight·comparisons·notices·disclaimer 포함). 결과가 오기 전에는 null */
    @Column(length = Length.LONG32)
    private String resultJson;

    /** 비교한 뒤 두 서류 중 하나라도 내용을 고치면 true */
    @Column(nullable = false)
    private boolean outdated;

    public Comparison(User user, Long baseDocumentId, Long targetDocumentId, CompareType compareType) {
        this.user = user;
        this.baseDocumentId = baseDocumentId;
        this.targetDocumentId = targetDocumentId;
        this.compareType = compareType;
    }

    public void complete(ComparisonStatus status, String resultJson) {
        this.status = status;
        this.resultJson = resultJson;
        this.outdated = false;
    }

    public boolean isCompleted() {
        return resultJson != null;
    }
}
