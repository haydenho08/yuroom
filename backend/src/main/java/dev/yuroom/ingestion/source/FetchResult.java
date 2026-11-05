package dev.yuroom.ingestion.source;

/**
 * The three things a polite fetch can produce. A sealed interface lets callers
 * {@code switch} over it exhaustively: the compiler complains if a case is forgotten.
 */
public sealed interface FetchResult {

    /** New bytes. {@code etag}/{@code lastModified} may be null. */
    record Fetched(byte[] body, String contentType, String etag, String lastModified) implements FetchResult {
    }

    /** The source confirmed nothing changed (HTTP 304). */
    record NotModified() implements FetchResult {
    }

    /** We chose not to fetch, or were told not to: robots.txt, 403, 429, 503... */
    record Blocked(String reason) implements FetchResult {
    }
}
