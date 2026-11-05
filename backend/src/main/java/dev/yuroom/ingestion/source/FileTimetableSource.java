package dev.yuroom.ingestion.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reads a timetable export from disk, e.g. a CSV the Registrar sends us, or a synthetic
 * sample in development. Change detection is done by the caller with a content hash.
 */
public final class FileTimetableSource implements TimetableSource {

    private final Path path;

    public FileTimetableSource(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    @Override
    public String id() {
        return "file";
    }

    @Override
    public FetchResult fetch(CacheValidators previous) throws IOException {
        return new FetchResult.Fetched(Files.readAllBytes(path), "text/csv", null, null);
    }

    @Override
    public String toString() {
        return "FileTimetableSource[" + path.toAbsolutePath().normalize() + "]";
    }
}
