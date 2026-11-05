package dev.yuroom.ingestion.parse;

/**
 * Turns the raw bytes of a source payload into meetings.
 * One implementation per source format (CSV export today; HTML could follow with Jsoup).
 */
public interface TimetableParser {

    /**
     * @throws TimetableParseException if the payload is structurally unusable
     *         (e.g. missing required columns). Individual bad rows are reported in the result instead.
     */
    ParseResult parse(byte[] content);
}
