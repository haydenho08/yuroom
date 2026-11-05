package dev.yuroom.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers ingestion once a day (and optionally at startup). Both paths go through the same
 * DB-backed min-interval guard, so restarts or a misconfigured cron can't exceed one fetch a day.
 */
@Component
@ConditionalOnProperty(prefix = "yuroom.ingestion", name = "enabled", havingValue = "true", matchIfMissing = true)
class IngestionScheduler {

    private final IngestionJob job;
    private final IngestionProperties props;

    IngestionScheduler(IngestionJob job, IngestionProperties props) {
        this.job = job;
        this.props = props;
    }

    @Scheduled(cron = "${yuroom.ingestion.cron:0 15 4 * * *}", zone = "America/Toronto")
    void daily() {
        job.run(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        if (props.runOnStartup()) {
            job.run(false);
        }
    }
}
