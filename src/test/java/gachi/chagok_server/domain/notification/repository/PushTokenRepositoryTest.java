package gachi.chagok_server.domain.notification.repository;

import gachi.chagok_server.domain.notification.entity.PushToken;
import gachi.chagok_server.domain.user.entity.User;
import gachi.chagok_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {"spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
class PushTokenRepositoryTest {

    @Autowired PushTokenRepository pushTokens;
    @Autowired UserRepository users;
    @Autowired EntityManager em;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = users.save(User.phoneUser("01012345678", "hash", "홍길동"));
        other = users.save(User.phoneUser("01099998888", "hash", "다른 사람"));
    }

    private void token(String deviceId, User user, String fcmToken) {
        PushToken token = new PushToken(deviceId, user);
        token.update(user, fcmToken, "IOS", true, "1.0");
        pushTokens.save(token);
    }

    @Test
    @DisplayName("만료된 FCM 토큰은 어느 기기에 있든 지운다")
    void deleteByFcmToken() {
        // given
        token("d1", owner, "expired");
        token("d2", owner, "alive");
        em.flush();
        em.clear();

        // when
        int deleted = pushTokens.deleteByFcmToken("expired");
        em.clear();

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(pushTokens.findById("d1")).isEmpty();
        assertThat(pushTokens.findById("d2")).isPresent();
    }

    @Test
    @DisplayName("같은 토큰이 다른 기기·사용자에게 있으면 지우고, 이 기기의 행과 다른 토큰은 남긴다")
    void deleteSameTokenOnOtherDevices() {
        // given: 계정을 바꿔 로그인해 같은 토큰이 다른 사용자의 다른 deviceId로 남아 있는 경우
        token("old-device", other, "shared-token");
        token("this-device", owner, "shared-token");
        token("unrelated", other, "unrelated-token");
        em.flush();
        em.clear();

        // when
        int deleted = pushTokens.deleteSameTokenOnOtherDevices("shared-token", "this-device");
        em.clear();

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(pushTokens.findById("old-device")).isEmpty();
        assertThat(pushTokens.findById("this-device")).isPresent();
        assertThat(pushTokens.findById("unrelated")).isPresent();
    }
}
