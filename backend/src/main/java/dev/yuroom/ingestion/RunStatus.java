package dev.yuroom.ingestion;

public enum RunStatus {
    /** In progress (or the process died mid-run). */
    RUNNING,
    /** New data parsed and stored. */
    SUCCESS,
    /** Source confirmed nothing changed (304 or identical hash); nothing re-parsed. */
    UNCHANGED,
    /** We were told not to fetch (robots.txt, 403, 429...). Not retried until the next day. */
    BLOCKED,
    /** Something broke; the previous timetable is still being served. */
    FAILED,
    /** Not persisted: the run never started because the last success was too recent. */
    SKIPPED
}
