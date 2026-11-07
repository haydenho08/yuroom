package dev.yuroom.reports;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuroom.testing.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClientKeysTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T15:00:00Z"));
    private final ClientKeys keys = new ClientKeys(clock, Duration.ofHours(24));

    @Test
    void sameAddressGivesSameKeyWithinARotation() {
        assertThat(keys.keyFor("203.0.113.7")).isEqualTo(keys.keyFor("203.0.113.7"));
        assertThat(keys.keyFor("203.0.113.7")).isNotEqualTo(keys.keyFor("203.0.113.8"));
    }

    @Test
    void keyRevealsNothingAboutTheAddress() {
        String key = keys.keyFor("203.0.113.7");
        assertThat(key).hasSize(64).doesNotContain("203").doesNotContain("113");
    }

    @Test
    void keysAreUnlinkableAcrossRotations() {
        String before = keys.keyFor("203.0.113.7");
        clock.advance(Duration.ofHours(24));
        assertThat(keys.keyFor("203.0.113.7")).isNotEqualTo(before);
    }

    @Test
    void differentInstancesUseDifferentSecrets() {
        // A restart gets a fresh secret too: keys can't be precomputed or carried over.
        assertThat(new ClientKeys(clock, Duration.ofHours(24)).keyFor("203.0.113.7"))
                .isNotEqualTo(keys.keyFor("203.0.113.7"));
    }
}
