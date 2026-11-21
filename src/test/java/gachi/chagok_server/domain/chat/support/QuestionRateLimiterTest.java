package gachi.chagok_server.domain.chat.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionRateLimiterTest {

    /** 테스트에서 시간을 직접 움직이는 시계 */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-18T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("1분에 10번까지 허용하고 11번째부터 막는다")
    void blocksAfterLimit() {
        // given
        MutableClock clock = new MutableClock();
        QuestionRateLimiter limiter = new QuestionRateLimiter(10, Duration.ofMinutes(1), clock);

        // when & then
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(1L)).isTrue();
        }
        assertThat(limiter.tryAcquire(1L)).isFalse();
    }

    @Test
    @DisplayName("1분이 지나면 다시 질문할 수 있다")
    void releasesAfterWindow() {
        // given
        MutableClock clock = new MutableClock();
        QuestionRateLimiter limiter = new QuestionRateLimiter(2, Duration.ofMinutes(1), clock);
        limiter.tryAcquire(1L);
        limiter.tryAcquire(1L);
        assertThat(limiter.tryAcquire(1L)).isFalse();

        // when
        clock.advance(Duration.ofSeconds(61));

        // then
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }

    @Test
    @DisplayName("제한은 사용자마다 따로 센다")
    void perUser() {
        // given
        QuestionRateLimiter limiter = new QuestionRateLimiter(1, Duration.ofMinutes(1), new MutableClock());

        // when & then
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();
    }

    @Test
    @DisplayName("막힌 요청은 횟수에 세지 않아 계속 눌러도 풀리는 시점이 늦어지지 않는다")
    void rejectedRequestsAreNotCounted() {
        // given
        MutableClock clock = new MutableClock();
        QuestionRateLimiter limiter = new QuestionRateLimiter(1, Duration.ofMinutes(1), clock);
        limiter.tryAcquire(1L);

        // when
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire(1L);
        clock.advance(Duration.ofSeconds(31));

        // then
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }
}
