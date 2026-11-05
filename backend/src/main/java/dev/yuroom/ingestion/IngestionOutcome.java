package dev.yuroom.ingestion;

/** What a call to {@link IngestionJob#run(boolean)} did. {@code runId} is null when SKIPPED. */
public record IngestionOutcome(RunStatus status, Long runId, String message) {
}
