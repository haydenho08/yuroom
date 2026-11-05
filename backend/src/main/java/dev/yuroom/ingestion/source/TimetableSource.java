package dev.yuroom.ingestion.source;

import java.io.IOException;

/**
 * Where timetable bytes come from. This is the seam from Stage 0: the rest of the app doesn't
 * know or care whether data arrives as a CSV export, an approved HTTP endpoint, or (later)
 * crowdsourced submissions.
 */
public interface TimetableSource {

    /** Stable identifier, stored with every snapshot and run (e.g. "file", "http:example.org"). */
    String id();

    /**
     * Fetch the current payload.
     *
     * @param previous cache validators from the last successful fetch, so HTTP sources can
     *                 send a conditional request and receive "304 Not Modified"
     */
    FetchResult fetch(CacheValidators previous) throws IOException, InterruptedException;
}
