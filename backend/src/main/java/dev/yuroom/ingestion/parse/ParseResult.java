package dev.yuroom.ingestion.parse;

import java.util.List;

/**
 * Outcome of parsing one file. Bad rows don't abort the parse; they are collected in
 * {@code errors} so the caller can decide whether the file as a whole is trustworthy.
 *
 * @param meetings   valid meetings (one per day, so a "TR" row yields two)
 * @param errors     rows that could not be parsed, with line numbers
 * @param rowsRead   data rows seen (excluding header and comments)
 * @param rowsSkipped rows intentionally ignored, e.g. online sections with no room
 */
public record ParseResult(List<ParsedMeeting> meetings, List<RowError> errors, int rowsRead, int rowsSkipped) {

    public ParseResult {
        meetings = List.copyOf(meetings);
        errors = List.copyOf(errors);
    }

    /** Fraction of data rows that were rejected (0.0 when the file is empty). */
    public double rejectRatio() {
        return rowsRead == 0 ? 0.0 : (double) errors.size() / rowsRead;
    }

    public record RowError(long line, String reason) {
    }
}
