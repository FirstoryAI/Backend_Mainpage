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

@Component
@RequiredArgsConstructor
public class EmailCheckRateLimiter {

    static final int LIMIT_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);
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
