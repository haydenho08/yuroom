package dev.yuroom.ingestion.parse;

/** The payload as a whole can't be parsed (as opposed to a single bad row). */
public class TimetableParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TimetableParseException(String message) {
        super(message);
    }

    public TimetableParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
