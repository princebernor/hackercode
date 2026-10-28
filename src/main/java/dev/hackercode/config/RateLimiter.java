package dev.hackercode.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window rate limiter. Adequate for a single application instance; a multi-instance
 * deployment should swap this for a shared store (e.g. Redis) behind the same interface.
 */
@Component
public class RateLimiter {
    private record Window(long startMillis, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Returns true if the action is allowed and counts it; false once {@code limit} is reached for the window. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long size = window.toMillis();
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.startMillis() >= size) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (w.count() < limit) {
                allowed[0] = true;
                return new Window(w.startMillis(), w.count() + 1);
            }
            return w;
        });
        return allowed[0];
    }

    public void reset() {
        windows.clear();
    }

    @Scheduled(fixedDelay = 600_000)
    void evictOld() {
        long cutoff = System.currentTimeMillis() - Duration.ofHours(2).toMillis();
        windows.entrySet().removeIf(e -> e.getValue().startMillis() < cutoff);
    }
}
