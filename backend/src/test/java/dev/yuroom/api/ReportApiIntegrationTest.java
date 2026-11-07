package dev.yuroom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yuroom.ingestion.IngestionJob;
import dev.yuroom.ingestion.RunStatus;
import dev.yuroom.testing.MutableClock;
import dev.yuroom.testing.PostgresTestcontainer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Crowd reports end to end, with a controllable clock. "Now" is Monday 2026-11-09 09:45 campus time.
 * At that moment LAS C and CLH A are free (see fixtures/api-timetable.csv).
 */
@SpringBootTest(properties = {
        "yuroom.ingestion.enabled=false",
        "yuroom.ingestion.source=FILE",
        "yuroom.ingestion.file.path=src/test/resources/fixtures/api-timetable.csv",
        "yuroom.reports.per-client-limit=3"
})
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, ReportApiIntegrationTest.Clocks.class})
class ReportApiIntegrationTest {

    static final Instant MONDAY_0945 = Instant.parse("2026-11-09T14:45:00Z");

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(MONDAY_0945);
        }
    }

    private static boolean seeded;
    private static int clientCounter;

    @Autowired
    MockMvc mvc;
    @Autowired
    MutableClock clock;
    @Autowired
    IngestionJob ingestion;
    @Autowired
    JdbcTemplate jdbc;

    /** Each test reports from its own address so rate limits don't leak between tests. */
    private String client;

    @BeforeEach
    void setUp() {
        if (!seeded) {
            assertThat(ingestion.run(true).status()).isEqualTo(RunStatus.SUCCESS);
            seeded = true;
        }
        jdbc.update("delete from occupancy_report");
        clock.set(MONDAY_0945);
        client = "198.51.100." + (++clientCounter);
    }

    private long roomId(String building, String number) {
        return jdbc.queryForObject("select r.id from room r join building b on b.id = r.building_id"
                + " where b.code = ? and r.number = ?", Long.class, building, number);
    }

    private ResultActions report(long roomId, String fromAddress) throws Exception {
        return mvc.perform(post("/api/rooms/" + roomId + "/reports").with(r -> {
            r.setRemoteAddr(fromAddress);
            return r;
        }));
    }

    @Test
    void reportIsAcceptedAndExpiresAfterAnHour() throws Exception {
        long lasC = roomId("LAS", "C");
        report(lasC, client)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(lasC))
                .andExpect(jsonPath("$.activeReports").value(1))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-09T15:45:00Z"));
    }

    @Test
    void reportedRoomsStayListedButMoveDownWithAWarning() throws Exception {
        // Without reports LAS C (free till 23:00) ranks above CLH A (free till 12:00).
        report(roomId("LAS", "C"), client).andExpect(status().isCreated());
        report(roomId("LAS", "C"), "198.51.100.200").andExpect(status().isCreated());

        mvc.perform(get("/api/rooms/free"))
                .andExpect(jsonPath("$.rooms[*].room", contains("A", "C")))
                .andExpect(jsonPath("$.rooms[0].occupiedReports").value(nullValue()))
                .andExpect(jsonPath("$.rooms[1].occupiedReports.count").value(2))
                .andExpect(jsonPath("$.rooms[1].occupiedReports.lastReportedAt").value("2026-11-09T14:45:00Z"));

        // An hour later the reports have expired. (LAS B's class has also ended by 10:46, so B and C
        // tie at "free until 23:00" and B sorts first by name; C is back in the top group.)
        clock.advance(Duration.ofMinutes(61));
        mvc.perform(get("/api/rooms/free"))
                .andExpect(jsonPath("$.rooms[*].room", contains("B", "C", "A")))
                .andExpect(jsonPath("$.rooms[1].occupiedReports").value(nullValue()));
    }

    @Test
    void reportsOnlyApplyToTheMomentTheyDescribe() throws Exception {
        report(roomId("LAS", "C"), client).andExpect(status().isCreated());
        mvc.perform(get("/api/rooms/free?at=2026-11-09T15:00")) // later the same day, after expiry
                .andExpect(jsonPath("$.rooms[0].occupiedReports").value(nullValue()));
        mvc.perform(get("/api/rooms/free?at=2026-11-09T10:00")) // 15 minutes later: still in effect
                .andExpect(jsonPath("$.rooms[?(@.room == 'C')].occupiedReports.count", contains(1)));
    }

    @Test
    void scheduleShowsReportsForTodayOnly() throws Exception {
        long lasC = roomId("LAS", "C");
        report(lasC, client).andExpect(status().isCreated());
        mvc.perform(get("/api/rooms/" + lasC + "/schedule"))
                .andExpect(jsonPath("$.occupiedReports.count").value(1));
        mvc.perform(get("/api/rooms/" + lasC + "/schedule?date=2026-11-10"))
                .andExpect(jsonPath("$.occupiedReports").value(nullValue()));
    }

    @Test
    void sameClientCannotReportTheSameRoomTwiceInHalfAnHour() throws Exception {
        long lasC = roomId("LAS", "C");
        report(lasC, client).andExpect(status().isCreated());
        report(lasC, client)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1800"))
                .andExpect(jsonPath("$.detail").value("You already reported this room recently."));

        clock.advance(Duration.ofMinutes(30));
        report(lasC, client).andExpect(status().isCreated());
    }

    @Test
    void oneClientCannotSweepAWholeBuilding() throws Exception {
        // per-client-limit is 3 in this test class (see properties above), 10 in production.
        List<Long> rooms = jdbc.queryForList("select id from room order by id", Long.class);
        assertThat(rooms).hasSize(4);
        for (int i = 0; i < 3; i++) {
            report(rooms.get(i), client).andExpect(status().isCreated());
        }
        report(rooms.get(3), client)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", containsString("Too many reports")));
        report(rooms.get(3), "198.51.100.250").andExpect(status().isCreated()); // other people unaffected

        clock.advance(Duration.ofHours(1));
        report(rooms.get(3), client).andExpect(status().isCreated());
    }

    @Test
    void unknownRoomIs404() throws Exception {
        report(999_999, client).andExpect(status().isNotFound());
    }

    @Test
    void nothingAboutTheReporterIsStored() throws Exception {
        report(roomId("LAS", "C"), client).andExpect(status().isCreated());
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'occupancy_report' order by 1",
                String.class);
        assertThat(columns).containsExactly("created_at", "expires_at", "id", "room_id");
    }
}
