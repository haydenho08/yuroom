package dev.yuroom.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads saved sample files from src/test/resources/fixtures. Tests never hit a live site. */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] fixture(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
