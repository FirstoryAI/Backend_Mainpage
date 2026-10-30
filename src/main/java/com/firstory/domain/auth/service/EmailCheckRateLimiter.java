package com.firstory.domain.auth.service;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이메일 중복 확인은 가입 여부를 알려 주므로 IP마다 1분에 10번으로 막는다 (계정 탐색 방지).
 * 횟수는 서버 메모리에만 있어 서버가 여러 대면 서버마다 따로 세고, 다시 띄우면 0부터 센다.
 * 서버를 늘리면 DB나 Redis로 옮긴다.
 */
@Component
@RequiredArgsConstructor
public class EmailCheckRateLimiter {

    static final int LIMIT_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);
    // 이만큼 쌓이면 끝난 창을 지운다
    static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void acquire(String ip) {
        Instant now = clock.instant();
        if (windows.size() >= CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isOver(now));
        }
        Window window = windows.compute(ip, (key, current) ->
                current == null || current.isOver(now) ? new Window(now, 1) : current.next());
        if (window.count() > LIMIT_PER_WINDOW) {
            throw new BusinessException(ErrorCode.EMAIL_CHECK_RATE_LIMITED);
        }
    }

    private record Window(Instant start, int count) {

        boolean isOver(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }

        Window next() {
            return new Window(start, count + 1);
        }
    }
}
