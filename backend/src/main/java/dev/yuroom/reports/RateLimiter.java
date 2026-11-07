package dev.yuroom.reports;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sliding-window log limiter: at most {@code limit} events per key within {@code window}.
 * Pure Java with an injected clock, so tests control time.
 *
 * <p>Memory is bounded: keys idle longer than the window are dropped on access, and if more than
 * {@code maxKeys} keys exist the least-recently-used one is evicted. Eviction can only make the
 * limiter more lenient for that key, never block an innocent user.
 */
public final class RateLimiter {

    /** Result of {@link #tryAcquire}: allowed, or denied with how long until a slot frees up. */
    public record Decision(boolean allowed, Duration retryAfter) {
        static final Decision ALLOWED = new Decision(true, Duration.ZERO);
    }

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> events;

    public RateLimiter(int limit, Duration window, Clock clock, int maxKeys) {
        if (limit < 1 || window.isNegative() || window.isZero() || maxKeys < 1) {
            throw new IllegalArgumentException("limit, window and maxKeys must be positive");
        }
        this.limit = limit;
        this.window = window;
        this.clock = Objects.requireNonNull(clock);
        this.events = new LinkedHashMap<>(16, 0.75f, true) { // access order = LRU
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
                return size() > maxKeys;
            }
        };
    }

    /** Records an event for {@code key} if allowed. Denied attempts are not recorded. */
    public synchronized Decision tryAcquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> log = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant cutoff = now.minus(window);
        while (!log.isEmpty() && !log.peekFirst().isAfter(cutoff)) {
            log.pollFirst();
        }
        if (log.size() >= limit) {
            return new Decision(false, Duration.between(cutoff, log.peekFirst()));
        }
        log.addLast(now);
        return Decision.ALLOWED;
    }

    /** Drops keys with no events inside the window. Called periodically; also bounds memory. */
    public synchronized int prune() {
        Instant cutoff = clock.instant().minus(window);
        int removed = 0;
        for (Iterator<Deque<Instant>> it = events.values().iterator(); it.hasNext(); ) {
            Deque<Instant> log = it.next();
            if (log.isEmpty() || !log.peekLast().isAfter(cutoff)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    synchronized int trackedKeys() {
        return events.size();
    }
}
