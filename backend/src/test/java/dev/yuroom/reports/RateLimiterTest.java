package dev.yuroom.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.yuroom.testing.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T15:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(3, Duration.ofHours(1), clock, 1000);

    @Test
    void allowsUpToTheLimitThenDeniesWithRetryAfter() {
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();

        RateLimiter.Decision denied = limiter.tryAcquire("a");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofMinutes(50)); // when the first event ages out
    }

    @Test
    void windowSlidesSoOldEventsStopCounting() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(30));
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.advance(Duration.ofMinutes(30)); // first event is now exactly an hour old
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).as("the other two are still in the window").isFalse();
    }

    @Test
    void deniedAttemptsDoNotExtendTheBlock() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        for (int i = 0; i < 20; i++) {
            limiter.tryAcquire("a"); // hammering while blocked
        }
        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void pruneDropsIdleKeys() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        clock.advance(Duration.ofMinutes(61));
        limiter.tryAcquire("c");
        assertThat(limiter.prune()).isEqualTo(2);
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void memoryIsBoundedByEvictingLeastRecentlyUsedKeys() {
        RateLimiter small = new RateLimiter(1, Duration.ofHours(1), clock, 2);
        small.tryAcquire("a");
        small.tryAcquire("b");
        small.tryAcquire("c"); // evicts "a"
        assertThat(small.trackedKeys()).isEqualTo(2);
        assertThat(small.tryAcquire("a").allowed()).as("eviction only ever makes us more lenient").isTrue();
        assertThat(small.tryAcquire("c").allowed()).isFalse();
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofHours(1), clock, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ZERO, clock, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
