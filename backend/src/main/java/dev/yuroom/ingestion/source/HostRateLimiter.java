package dev.yuroom.ingestion.source;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Guarantees at least {@code minInterval} between consecutive requests to the same host,
 * by sleeping if a caller arrives too early. Clock and sleep are injected so tests run instantly.
 */
public final class HostRateLimiter {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final Duration minInterval;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, Instant> lastRequest = new HashMap<>();

    public HostRateLimiter(Duration minInterval, Clock clock, Sleeper sleeper) {
        if (minInterval.isNegative()) {
            throw new IllegalArgumentException("minInterval must not be negative");
        }
        this.minInterval = minInterval;
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    public static HostRateLimiter realTime(Duration minInterval) {
        return new HostRateLimiter(minInterval, Clock.systemUTC(), d -> Thread.sleep(d.toMillis()));
    }

    /** Blocks until a request to {@code host} is allowed, then records it. */
    public synchronized void acquire(String host) throws InterruptedException {
        Instant previous = lastRequest.get(host);
        if (previous != null) {
            Duration wait = Duration.between(clock.instant(), previous.plus(minInterval));
            if (wait.isPositive()) {
                sleeper.sleep(wait);
            }
        }
        lastRequest.put(host, clock.instant());
    }
}
