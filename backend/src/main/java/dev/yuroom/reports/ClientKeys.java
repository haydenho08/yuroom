package dev.yuroom.reports;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns a client address into an opaque rate-limiting key: HMAC-SHA256 with a random secret that
 * lives only in memory and is replaced every {@code rotation}.
 *
 * <p>Why not store or hash the IP directly? A plain SHA-256 of an IPv4 address can be reversed by
 * trying all 4 billion addresses in minutes. With a secret key that is never written anywhere and is
 * thrown away daily, yesterday's keys can't be linked to anyone, even by us, and nothing is persisted.
 */
public final class ClientKeys {

    private final Clock clock;
    private final Duration rotation;
    private final SecureRandom random = new SecureRandom();
    private byte[] secret;
    private Instant secretCreatedAt;

    public ClientKeys(Clock clock, Duration rotation) {
        this.clock = Objects.requireNonNull(clock);
        this.rotation = Objects.requireNonNull(rotation);
        rotate();
    }

    public synchronized String keyFor(String clientAddress) {
        if (!clock.instant().isBefore(secretCreatedAt.plus(rotation))) {
            rotate();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(clientAddress.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    private void rotate() {
        secret = new byte[32];
        random.nextBytes(secret);
        secretCreatedAt = clock.instant();
    }
}
