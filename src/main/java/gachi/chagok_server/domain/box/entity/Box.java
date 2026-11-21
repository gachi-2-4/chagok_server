package gachi.chagok_server.domain.box.entity;

import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자가 만드는 서류 묶음.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "boxes", uniqueConstraints = @UniqueConstraint(name = "uk_boxes_user_name", columnNames = {"user_id", "name"}))
public class Box extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoxColor color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoxIcon icon;

    /** 박스 상세 조회를 마지막으로 부른 일시. 한 번도 열지 않았으면 null. */
    private Instant lastOpenedAt;

    public Box(User user, String name, BoxColor color, BoxIcon icon) {
        this.user = user;
        this.name = name;
        this.color = color;
        this.icon = icon;
    }

    /** 보낸 값(null이 아닌 것)만 바꾼다. */
    public void change(String name, BoxColor color, BoxIcon icon) {
        if (name != null) {
            this.name = name;
        }
        if (color != null) {
            this.color = color;
        }
        if (icon != null) {
            this.icon = icon;
        }
    }

    public void open(Instant now) {
        this.lastOpenedAt = now;
    }
}
