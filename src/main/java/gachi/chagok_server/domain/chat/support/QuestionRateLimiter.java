package gachi.chagok_server.domain.chat.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자당 분당 10회로 질문을 제한한다 (AI 서버 제한과 같음).
 * 서버 한 대의 메모리에서만 세므로 서버를 여러 대로 늘리면 공유 저장소(Redis 등)로 옮겨야 한다.
 */
@Component
public class QuestionRateLimiter {

    static final int MAX_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Deque<Long>> requests = new ConcurrentHashMap<>();

    public QuestionRateLimiter() {
        this(MAX_PER_WINDOW, WINDOW, Clock.systemUTC());
    }

    QuestionRateLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    /** 허용되면 이번 요청을 세고 true, 한도를 넘었으면 세지 않고 false. */
    public boolean tryAcquire(Long userId) {
        long now = clock.millis();
        long windowStart = now - window.toMillis();
        Deque<Long> timestamps = requests.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= max) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
