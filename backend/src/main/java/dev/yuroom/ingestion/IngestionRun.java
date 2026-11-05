package dev.yuroom.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit log of every ingestion attempt: what happened, when, and why. */
@Entity
@Table(name = "ingestion_run")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "meetings_parsed")
    private Integer meetingsParsed;

    @Column(name = "rows_rejected")
    private Integer rowsRejected;

    @Column(columnDefinition = "text")
    private String message;

    protected IngestionRun() {
        // for JPA
    }

    public static IngestionRun start(String source, Instant now) {
        IngestionRun run = new IngestionRun();
        run.source = source;
        run.startedAt = now;
        run.status = RunStatus.RUNNING;
        return run;
    }

    public void finish(RunStatus status, Instant now, Long snapshotId, Integer meetingsParsed,
                       Integer rowsRejected, String message) {
        if (status == RunStatus.RUNNING || status == RunStatus.SKIPPED) {
            throw new IllegalArgumentException("cannot finish a run as " + status);
        }
        this.status = status;
        this.finishedAt = now;
        this.snapshotId = snapshotId;
        this.meetingsParsed = meetingsParsed;
        this.rowsRejected = rowsRejected;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public Integer getMeetingsParsed() {
        return meetingsParsed;
    }

    public Integer getRowsRejected() {
        return rowsRejected;
    }

    public String getMessage() {
        return message;
    }
}
