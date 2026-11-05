package dev.yuroom.ingestion.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostRateLimiterTest {

    /** A clock we move by hand; "sleeping" just advances it. No real waiting in tests. */
    static final class FakeClock extends Clock {
        Instant now = Instant.parse("2026-11-05T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final FakeClock clock = new FakeClock();
    private final List<Duration> sleeps = new ArrayList<>();
    private final HostRateLimiter limiter = new HostRateLimiter(Duration.ofSeconds(10), clock, d -> {
        sleeps.add(d);
        clock.now = clock.now.plus(d);
    });

    @Test
    void firstRequestDoesNotWait() throws InterruptedException {
        limiter.acquire("example.org");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void backToBackRequestsWaitTheRemainder() throws InterruptedException {
        limiter.acquire("example.org");
        clock.now = clock.now.plusSeconds(3);
        limiter.acquire("example.org");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void requestsAfterTheIntervalDoNotWait() throws InterruptedException {
        limiter.acquire("example.org");
        clock.now = clock.now.plusSeconds(10);
        limiter.acquire("example.org");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void hostsAreLimitedIndependently() throws InterruptedException {
        limiter.acquire("a.example.org");
        limiter.acquire("b.example.org");
        assertThat(sleeps).isEmpty();
    }
}
