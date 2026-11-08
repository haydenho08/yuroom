package dev.yuroom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Production web behaviour (serving the React app, caching, security headers, /api/info) and the
 * anonymous usage counters. "Now" is Wednesday 2026-11-11 10:00 campus time.
 */
@SpringBootTest(properties = {
        "yuroom.ingestion.enabled=false",
        "yuroom.ingestion.source=FILE",
        "yuroom.ingestion.file.path=src/test/resources/fixtures/api-timetable.csv",
        "yuroom.demo-data=true"
})
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, WebAndUsageIntegrationTest.Clocks.class})
class WebAndUsageIntegrationTest {

    static final Instant WEDNESDAY_1000 = Instant.parse("2026-11-11T15:00:00Z");

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(WEDNESDAY_1000);
        }
    }

    private static int clientCounter;

    @Autowired
    MockMvc mvc;
    @Autowired
    MutableClock clock;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    IngestionJob ingestion;

    private String client;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from usage_daily");
        clock.set(WEDNESDAY_1000);
        client = "203.0.113." + (++clientCounter);
    }

    private ResultActions sendUsage(String json) throws Exception {
        return mvc.perform(post("/api/usage").contentType(MediaType.APPLICATION_JSON).content(json).with(r -> {
            r.setRemoteAddr(client);
            return r;
        }));
    }

    @Nested
    class ServingTheApp {

        @Test
        void clientSideRoutesGetTheAppShellUncached() throws Exception {
            // "/" itself is covered against real Tomcat in RealTomcatIntegrationTest: Spring forwards it
            // to index.html, and MockMvc doesn't follow forwards.
            for (String path : List.of("/rooms/42", "/some/deep/link")) {
                mvc.perform(get(path))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("spa-shell")))
                        .andExpect(header().string("Cache-Control", "no-cache"));
            }
        }

        @Test
        void hashedAssetsAreCachedForAYear() throws Exception {
            mvc.perform(get("/assets/index-abc123.js"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        }

        @Test
        void missingFilesAndUnknownApiUrlsAre404sNotTheAppShell() throws Exception {
            mvc.perform(get("/assets/gone-999.js")).andExpect(status().isNotFound());
            mvc.perform(get("/missing.png")).andExpect(status().isNotFound());
            mvc.perform(get("/api/nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void everyResponseCarriesSecurityHeaders() throws Exception {
            for (String path : List.of("/rooms/1", "/api/info")) {
                mvc.perform(get(path))
                        .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                        .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andExpect(header().string("Referrer-Policy", "no-referrer"))
                        .andExpect(header().string("Permissions-Policy", containsString("geolocation=(self)")))
                        .andExpect(header().doesNotExist("Strict-Transport-Security")); // plain http here
            }
        }

        @Test
        void infoSaysWhenDataIsSynthetic() throws Exception {
            mvc.perform(get("/api/info")).andExpect(jsonPath("$.demoData").value(true));
        }
    }

    @Nested
    class UsageCounters {

        private Map<String, Object> currentWeek() {
            return jdbc.queryForList("select event, count from usage_daily where day = '2026-11-11'").stream()
                    .collect(java.util.stream.Collectors.toMap(r -> (String) r.get("event"), r -> r.get("count")));
        }

        @Test
        void countsEventsAndStoresNothingElse() throws Exception {
            sendUsage("{\"events\":[\"visit\",\"daily_visitor\",\"weekly_visitor\"]}").andExpect(status().isNoContent());
            sendUsage("{\"events\":[\"visit\"]}").andExpect(status().isNoContent());

            assertThat(currentWeek()).containsEntry("visit", 2L).containsEntry("daily_visitor", 1L)
                    .containsEntry("weekly_visitor", 1L);
            // The whole analytics schema: a day, an event name and a number.
            assertThat(jdbc.queryForList("select column_name from information_schema.columns"
                    + " where table_name = 'usage_daily' order by ordinal_position", String.class))
                    .containsExactly("day", "event", "count");
        }

        @Test
        void weeklyTotalsArePublic() throws Exception {
            sendUsage("{\"events\":[\"visit\",\"daily_visitor\",\"weekly_visitor\"]}");
            client = "203.0.113.250";
            sendUsage("{\"events\":[\"visit\",\"daily_visitor\",\"weekly_visitor\",\"near_me\"]}");

            mvc.perform(get("/api/usage/weekly?weeks=2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weeks.length()").value(2))
                    .andExpect(jsonPath("$.weeks[1].weekStart").value("2026-11-09"))
                    .andExpect(jsonPath("$.weeks[1].visits").value(2))
                    .andExpect(jsonPath("$.weeks[1].weeklyVisitors").value(2))
                    .andExpect(jsonPath("$.weeks[1].avgDailyVisitors").value(0.7)) // 2 over Mon-Wed
                    .andExpect(jsonPath("$.weeks[1].nearMeUses").value(1))
                    .andExpect(jsonPath("$.weeks[1].complete").value(false))
                    .andExpect(jsonPath("$.note", containsString("per browser, not per person")));
        }

        @Test
        void daysFollowCampusTimeNotUtc() throws Exception {
            clock.set(Instant.parse("2026-11-12T03:30:00Z")); // 22:30 Wednesday in Toronto, Thursday in UTC
            sendUsage("{\"events\":[\"visit\"]}");
            assertThat(currentWeek()).containsEntry("visit", 1L);
        }

        @Test
        void rejectsUnknownServerOnlyAndEmptyEvents() throws Exception {
            sendUsage("{\"events\":[\"page_view\"]}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("unknown event 'page_view'")));
            sendUsage("{\"events\":[\"report\"]}").andExpect(status().isBadRequest()); // only the server counts reports
            sendUsage("{\"events\":[]}").andExpect(status().isBadRequest());
            sendUsage("{\"events\":[\"visit\",\"visit\",\"visit\",\"visit\",\"visit\"]}").andExpect(status().isBadRequest());
            sendUsage("not json").andExpect(status().isBadRequest());
            assertThat(currentWeek()).isEmpty();
        }

        @Test
        void oneConnectionCannotInflateTheNumbers() throws Exception {
            for (int i = 0; i < 30; i++) {
                sendUsage("{\"events\":[\"visit\"]}").andExpect(status().isNoContent());
            }
            sendUsage("{\"events\":[\"visit\"]}").andExpect(status().isTooManyRequests());
            assertThat(currentWeek()).containsEntry("visit", 30L);

            clock.advance(Duration.ofMinutes(61));
            sendUsage("{\"events\":[\"visit\"]}").andExpect(status().isNoContent());
        }

        @Test
        void acceptedReportsAreCountedByTheServer() throws Exception {
            if (jdbc.queryForObject("select count(*) from room", Long.class) == 0) {
                assertThat(ingestion.run(true).status()).isEqualTo(RunStatus.SUCCESS);
            }
            long room = jdbc.queryForObject("select min(id) from room", Long.class);
            mvc.perform(post("/api/rooms/" + room + "/reports").with(r -> {
                r.setRemoteAddr(client);
                return r;
            })).andExpect(status().isCreated());
            assertThat(currentWeek()).containsEntry("report", 1L);
        }
    }
}
