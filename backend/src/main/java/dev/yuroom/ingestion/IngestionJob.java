package dev.yuroom.ingestion;

import dev.yuroom.ingestion.parse.ParseResult;
import dev.yuroom.ingestion.parse.TimetableParser;
import dev.yuroom.ingestion.source.CacheValidators;
import dev.yuroom.ingestion.source.FetchResult;
import dev.yuroom.ingestion.source.TimetableSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one ingestion run: guard → fetch → dedupe → store raw → parse → validate → import.
 *
 * <p>Deliberately <i>not</i> one big transaction. The network fetch happens outside any
 * transaction (never hold a DB connection while waiting on a remote server), the raw snapshot
 * is committed on its own (so we keep the bytes even if parsing fails), and only the import
 * itself is atomic.
 */
@Service
public class IngestionJob {

    private static final Logger log = LoggerFactory.getLogger(IngestionJob.class);
    private static final EnumSet<RunStatus> GOOD = EnumSet.of(RunStatus.SUCCESS, RunStatus.UNCHANGED);

    private final TimetableSource source;
    private final TimetableParser parser;
    private final TimetableImporter importer;
    private final RawSnapshotRepository snapshots;
    private final IngestionRunRepository runs;
    private final IngestionProperties props;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public IngestionJob(TimetableSource source, TimetableParser parser, TimetableImporter importer,
                        RawSnapshotRepository snapshots, IngestionRunRepository runs,
                        IngestionProperties props, Clock clock) {
        this.source = source;
        this.parser = parser;
        this.importer = importer;
        this.snapshots = snapshots;
        this.runs = runs;
        this.props = props;
        this.clock = clock;
    }

    /**
     * @param force skip the "at most once per {@code min-interval}" guard (manual/admin runs)
     */
    public IngestionOutcome run(boolean force) {
        if (!running.compareAndSet(false, true)) {
            return new IngestionOutcome(RunStatus.SKIPPED, null, "another run is in progress");
        }
        try {
            return doRun(force);
        } finally {
            running.set(false);
        }
    }

    private IngestionOutcome doRun(boolean force) {
        String sourceId = source.id();
        Instant now = clock.instant();

        if (!force) {
            // Every attempt counts (even FAILED/BLOCKED), and it's read from the DB, not memory,
            // so neither errors nor restarts can turn "once a day" into "once a minute".
            Optional<IngestionRun> last = runs.findFirstBySourceOrderByStartedAtDesc(sourceId);
            if (last.isPresent() && last.get().getStartedAt().plus(props.minInterval()).isAfter(now)) {
                String msg = "last attempt at " + last.get().getStartedAt() + " (" + last.get().getStatus()
                        + ") is within min-interval " + props.minInterval();
                log.info("Ingestion skipped: {}", msg);
                return new IngestionOutcome(RunStatus.SKIPPED, null, msg);
            }
        }

        IngestionRun run = runs.save(IngestionRun.start(sourceId, now));
        try {
            Optional<RawSnapshotRepository.SnapshotMeta> previous = lastGoodSnapshot(sourceId);
            CacheValidators validators = previous
                    .map(p -> new CacheValidators(p.getEtag(), p.getLastModified()))
                    .orElse(CacheValidators.none());

            FetchResult fetched = source.fetch(validators);
            return switch (fetched) {
                case FetchResult.NotModified n -> finish(run, RunStatus.UNCHANGED,
                        previous.map(RawSnapshotRepository.SnapshotMeta::getId).orElse(null), null, null,
                        "source returned 304 Not Modified");
                case FetchResult.Blocked b -> finish(run, RunStatus.BLOCKED, null, null, null, b.reason());
                case FetchResult.Fetched f -> ingest(run, sourceId, f, previous);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return finish(run, RunStatus.FAILED, null, null, null, "interrupted");
        } catch (Exception e) {
            log.error("Ingestion run {} failed", run.getId(), e);
            return finish(run, RunStatus.FAILED, null, null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private IngestionOutcome ingest(IngestionRun run, String sourceId, FetchResult.Fetched f,
                                    Optional<RawSnapshotRepository.SnapshotMeta> previous) {
        String sha = sha256(f.body());
        if (previous.isPresent() && previous.get().getContentSha256().equals(sha)) {
            return finish(run, RunStatus.UNCHANGED, previous.get().getId(), null, null,
                    "content identical to snapshot " + previous.get().getId());
        }

        // Keep the raw bytes (committed on their own) before we try to understand them.
        Long snapshotId = snapshots.findMetaBySourceAndContentSha256(sourceId, sha)
                .map(RawSnapshotRepository.SnapshotMeta::getId)
                .orElseGet(() -> snapshots.save(new RawSnapshot(sourceId, clock.instant(), sha, f.body(),
                        f.contentType(), f.etag(), f.lastModified())).getId());

        ParseResult parsed = parser.parse(f.body());
        int rejected = parsed.errors().size();
        if (parsed.meetings().isEmpty()) {
            return finish(run, RunStatus.FAILED, snapshotId, 0, rejected,
                    "no meetings parsed; keeping the previous timetable" + firstErrors(parsed));
        }
        if (parsed.rejectRatio() > props.maxRejectRatio()) {
            return finish(run, RunStatus.FAILED, snapshotId, parsed.meetings().size(), rejected,
                    String.format("%.0f%% of rows rejected (limit %.0f%%); keeping the previous timetable%s",
                            parsed.rejectRatio() * 100, props.maxRejectRatio() * 100, firstErrors(parsed)));
        }

        TimetableImporter.ImportStats stats = importer.replaceTimetable(parsed.meetings(), snapshotId);
        String msg = String.format("imported %d meetings across %d term(s); %d new buildings, %d new rooms;"
                        + " %d rows rejected, %d skipped (no room)%s",
                stats.meetings(), stats.terms(), stats.newBuildings(), stats.newRooms(), rejected,
                parsed.rowsSkipped(), firstErrors(parsed));
        return finish(run, RunStatus.SUCCESS, snapshotId, stats.meetings(), rejected, msg);
    }

    private Optional<RawSnapshotRepository.SnapshotMeta> lastGoodSnapshot(String sourceId) {
        return runs.findFirstBySourceAndStatusInOrderByStartedAtDesc(sourceId, GOOD)
                .map(IngestionRun::getSnapshotId)
                .flatMap(snapshots::findMetaById);
    }

    private IngestionOutcome finish(IngestionRun run, RunStatus status, Long snapshotId, Integer parsed,
                                    Integer rejected, String message) {
        run.finish(status, clock.instant(), snapshotId, parsed, rejected, message);
        runs.save(run);
        if (status == RunStatus.FAILED) {
            log.warn("Ingestion run {} {}: {}", run.getId(), status, message);
        } else {
            log.info("Ingestion run {} {}: {}", run.getId(), status, message);
        }
        return new IngestionOutcome(status, run.getId(), message);
    }

    private static String firstErrors(ParseResult parsed) {
        if (parsed.errors().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(". First errors:");
        parsed.errors().stream().limit(5)
                .forEach(e -> sb.append(" [line ").append(e.line()).append(": ").append(e.reason()).append(']'));
        return sb.toString();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
