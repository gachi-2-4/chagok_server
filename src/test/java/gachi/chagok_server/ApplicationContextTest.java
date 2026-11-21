package gachi.chagok_server;

import gachi.chagok_server.domain.ai.client.AiServerClient;
import gachi.chagok_server.domain.box.dto.BoxDtos;
import gachi.chagok_server.domain.box.entity.BoxColor;
import gachi.chagok_server.domain.box.entity.BoxIcon;
import gachi.chagok_server.domain.deadline.dto.DeadlineDtos;
import gachi.chagok_server.domain.deadline.entity.DeadlineSource;
import gachi.chagok_server.domain.deadline.entity.DeadlineType;
import gachi.chagok_server.domain.notification.push.PushSender;
import gachi.chagok_server.global.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 빈이 순환 없이 연결되고, 설정 값(S3·AI 서버)이 제대로 읽히는지 확인하는 스모크 테스트.
 * 실제 DB·S3·AI 서버 없이 H2와 더미 설정으로 띄운다 (외부 서버에는 연결하지 않는다).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "auth.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "auth.sms.hash-secret=test-hash-secret",
        "auth.sms.twilio.account-sid=ACtest",
        "auth.sms.twilio.auth-token=test",
        "auth.sms.twilio.from=+10000000000",
        "auth.oauth.kakao.client-id=test",
        "auth.oauth.kakao.app-id=1",
        "auth.oauth.kakao.redirect-uri=http://localhost/kakao",
        "auth.oauth.google.client-id=test",
        "auth.oauth.google.client-secret=test",
        "auth.oauth.google.redirect-uri=http://localhost/google",
        "chagok.internal.key=internal-test-key",
        "chagok.storage.bucket=test-bucket",
        "chagok.storage.region=ap-northeast-2",
        "chagok.ai.base-url=http://localhost:9",
        "chagok.ai.api-key=test-api-key"
})
class ApplicationContextTest {

    @Autowired ApplicationContext context;
    @Autowired JsonMapper jsonMapper;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 순환 의존 없이 뜨고 주요 연동 빈이 등록된다")
    void contextLoads() {
        assertThat(context.getBean(FileStorage.class)).isNotNull();
        assertThat(context.getBean(AiServerClient.class)).isNotNull();
        assertThat(context.getBean(PushSender.class)).isNotNull();
    }

    @Test
    @DisplayName("응답 JSON은 명세대로 isCompleted 이름과 KST 오프셋 일시, null 필드를 그대로 낸다")
    void responseJsonMatchesSpec() {
        // given
        var item = new DeadlineDtos.ListItem(75L, LocalDate.of(2026, 11, 25), 13, DeadlineType.PAYMENT, "관리비 납부", null,
                new DeadlineDtos.DocumentRef(540L, "11월 관리비 고지서"), new DeadlineDtos.BoxRef(1L, "집 · 임대"),
                DeadlineSource.AI, false, null);
        var created = new BoxDtos.Created(6L, "반려동물", BoxColor.GREEN, BoxIcon.PET, 0, null,
                java.time.Instant.parse("2026-11-12T05:00:00Z").atOffset(java.time.ZoneOffset.ofHours(9)));

        // when
        JsonNode deadline = jsonMapper.readTree(jsonMapper.writeValueAsString(item));
        JsonNode box = jsonMapper.readTree(jsonMapper.writeValueAsString(created));

        // then
        assertThat(deadline.has("isCompleted")).isTrue();
        assertThat(deadline.get("isCompleted").asBoolean()).isFalse();
        assertThat(deadline.get("date").asString()).isEqualTo("2026-11-25");
        assertThat(deadline.has("amount")).isTrue();
        assertThat(deadline.get("amount").isNull()).isTrue();
        assertThat(deadline.get("reminderId").isNull()).isTrue();
        assertThat(box.get("createdAt").asString()).isEqualTo("2026-11-12T14:00:00+09:00");
        assertThat(box.get("nearestDeadline").isNull()).isTrue();
    }
}
