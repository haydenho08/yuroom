package dev.yuroom.ingestion;

import static dev.yuroom.testing.Fixtures.fixture;
import static org.assertj.core.api.Assertions.assertThat;

import dev.yuroom.ingestion.source.CacheValidators;
import dev.yuroom.ingestion.source.FetchResult;
import dev.yuroom.ingestion.source.TimetableSource;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end ingestion against a real PostgreSQL 16 in Docker: Flyway migrations, Hibernate
 * schema validation, constraints, transactions, the lot. H2 would hide Postgres-specific behaviour.
 */
@Testcontainers
@SpringBootTest(properties = {
        "yuroom.ingestion.enabled=false", // no scheduler; tests drive the job directly
        "yuroom.ingestion.min-interval=20h"
})
class IngestionJobIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** A source whose next result each test sets directly. */
    static final class StubSource implements TimetableSource {
        volatile FetchResult next;

        @Override
        public String id() {
            return "stub";
        }

        @Override
        public FetchResult fetch(CacheValidators previous) {
            return next;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        StubSource stubSource() {
            return new StubSource();
        }
    }

    @Autowired
    IngestionJob job;
    @Autowired
    StubSource source;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate meeting, room, building, term, ingestion_run, raw_snapshot restart identity cascade");
    }

    private void serve(String fixtureName) {
        source.next = new FetchResult.Fetched(fixture(fixtureName), "text/csv", null, null);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void firstRunStoresRawBytesAndParsedTimetable() {
        serve("valid.csv");

        IngestionOutcome outcome = job.run(false);

        assertThat(outcome.status()).as(outcome.message()).isEqualTo(RunStatus.SUCCESS);
        assertThat(count("meeting")).isEqualTo(7);
        assertThat(count("building")).isEqualTo(4);  // LAS, CLH, VH, ACW
        assertThat(count("room")).isEqualTo(4);      // LAS A, CLH H, VH 1152, ACW 109
        assertThat(count("raw_snapshot")).isEqualTo(1);
        byte[] stored = jdbc.queryForObject("select content from raw_snapshot", byte[].class);
        assertThat(stored).isEqualTo(fixture("valid.csv"));

        LocalDate start = jdbc.queryForObject("select start_date from term where code = '2026FA'", LocalDate.class);
        LocalDate end = jdbc.queryForObject("select end_date from term where code = '2026FA'", LocalDate.class);
        assertThat(start).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(end).isEqualTo(LocalDate.of(2026, 12, 8));
        assertThat(jdbc.queryForObject("select campus from building where code = 'LAS'", String.class))
                .isEqualTo("Keele");
        assertThat(jdbc.queryForObject("select campus from building where code = 'VH'", String.class))
                .as("campus left blank in the file stays null").isNull();
    }

    @Test
    void wallClockTimesAndDatesAreStoredExactlyRegardlessOfJvmZone() {
        // Regression: hibernate.jdbc.time_zone=UTC once stored "08:30" as "13:30". The test JVM runs
        // in Pacific/Auckland (see build.gradle), so any hidden zone conversion shows up here.
        serve("valid.csv");
        job.run(false);

        assertThat(jdbc.queryForList("""
                select m.start_time::text || '-' || m.end_time::text || ' ' || m.start_date::text
                from meeting m join room r on r.id = m.room_id join building b on b.id = r.building_id
                where b.code = 'CLH' or (b.code = 'LAS' and m.day_of_week = 1)
                order by m.start_time""", String.class))
                .containsExactly("08:30:00-10:00:00 2026-09-09", "19:00:00-22:00:00 2026-09-10");
        assertThat(jdbc.queryForObject("select current_setting('TimeZone')", String.class))
                .as("sanity: zone used by the DB session").isNotBlank();
    }

    @Test
    void secondRunWithinMinIntervalIsSkippedWithoutFetching() {
        serve("valid.csv");
        job.run(false);
        source.next = null; // would NPE if fetched

        IngestionOutcome outcome = job.run(false);

        assertThat(outcome.status()).isEqualTo(RunStatus.SKIPPED);
        assertThat(count("ingestion_run")).isEqualTo(1);
    }

    @Test
    void identicalContentIsRecognisedByHashAndNotReparsed() {
        serve("valid.csv");
        job.run(false);
        long firstMeetingId = jdbc.queryForObject("select min(id) from meeting", Long.class);

        IngestionOutcome outcome = job.run(true);

        assertThat(outcome.status()).isEqualTo(RunStatus.UNCHANGED);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select min(id) from meeting", Long.class))
                .as("meetings untouched").isEqualTo(firstMeetingId);
    }

    @Test
    void changedContentReplacesTheTermsMeetings() {
        serve("valid.csv");
        job.run(false);
        serve("updated.csv");

        IngestionOutcome outcome = job.run(true);

        assertThat(outcome.status()).as(outcome.message()).isEqualTo(RunStatus.SUCCESS);
        assertThat(count("meeting")).isEqualTo(4); // MW + TR
        assertThat(count("raw_snapshot")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from meeting m join room r on r.id = m.room_id where r.number = 'C'", Integer.class))
                .isEqualTo(2);
        assertThat(count("room")).as("old rooms are kept; rooms don't disappear").isEqualTo(5);
    }

    @Test
    void mostlyBrokenFileFailsAndKeepsThePreviousTimetable() {
        serve("valid.csv");
        job.run(false);
        serve("with-errors.csv"); // 9 of 11 rows rejected

        IngestionOutcome outcome = job.run(true);

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.message()).contains("82% of rows rejected").contains("line 3");
        assertThat(count("meeting")).as("previous timetable still served").isEqualTo(7);
        assertThat(count("raw_snapshot")).as("bad payload kept for debugging").isEqualTo(2);
    }

    @Test
    void structurallyInvalidFileFailsWithoutTouchingData() {
        serve("valid.csv");
        job.run(false);
        serve("missing-columns.csv");

        IngestionOutcome outcome = job.run(true);

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.message()).contains("missing required column(s)");
        assertThat(count("meeting")).isEqualTo(7);
    }

    @Test
    void blockedSourceIsRecordedAndCountsTowardsTheDailyLimit() {
        source.next = new FetchResult.Blocked("robots.txt does not allow /timetable.csv");

        assertThat(job.run(false).status()).isEqualTo(RunStatus.BLOCKED);
        assertThat(job.run(false).status()).as("no retry until tomorrow").isEqualTo(RunStatus.SKIPPED);
        assertThat(count("meeting")).isZero();
    }

    @Test
    void notModifiedIsRecordedAsUnchanged() {
        serve("valid.csv");
        job.run(false);
        source.next = new FetchResult.NotModified();

        assertThat(job.run(true).status()).isEqualTo(RunStatus.UNCHANGED);
        assertThat(count("meeting")).isEqualTo(7);
    }
}
