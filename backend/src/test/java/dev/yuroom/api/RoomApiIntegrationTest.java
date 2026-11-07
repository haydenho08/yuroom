package dev.yuroom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yuroom.ingestion.IngestionJob;
import dev.yuroom.ingestion.RunStatus;
import dev.yuroom.testing.PostgresTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The whole stack: HTTP → controller → service → JPA → Postgres, with data loaded through the
 * real ingestion pipeline. 2026-11-09 is a Monday in the fall term.
 */
@SpringBootTest(properties = {
        "yuroom.ingestion.enabled=false",
        "yuroom.ingestion.source=FILE",
        "yuroom.ingestion.file.path=src/test/resources/fixtures/api-timetable.csv"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class RoomApiIntegrationTest {

    private static boolean seeded;

    @Autowired
    MockMvc mvc;
    @Autowired
    IngestionJob ingestion;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seedOnce() {
        if (!seeded) {
            assertThat(ingestion.run(true).status()).isEqualTo(RunStatus.SUCCESS);
            seeded = true;
        }
    }

    private long roomId(String building, String number) {
        return jdbc.queryForObject("select r.id from room r join building b on b.id = r.building_id"
                + " where b.code = ? and r.number = ?", Long.class, building, number);
    }

    private ResultActions free(String query) throws Exception {
        return mvc.perform(get("/api/rooms/free" + query));
    }

    @Test
    void buildingsListWithRoomCounts() throws Exception {
        mvc.perform(get("/api/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("CLH", "LAS")))
                .andExpect(jsonPath("$[1].roomCount").value(3))
                .andExpect(jsonPath("$[1].campus").value("Keele"));
    }

    @Test
    void freeRoomsSortedByHowLongTheyStayFree() throws Exception {
        // 09:45 Monday: LAS A busy (8:30-11:30 merged), LAS B busy (9:30-10:30),
        // LAS C has no Monday class, CLH A free until its 12:00 class.
        free("?at=2026-11-09T09:45")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.at").value("2026-11-09T09:45:00-05:00"))
                .andExpect(jsonPath("$.term").value("2026FA"))
                .andExpect(jsonPath("$.timetableDay").value("MONDAY"))
                .andExpect(jsonPath("$.notice").value(nullValue()))
                .andExpect(jsonPath("$.hours.open").value("07:00"))
                .andExpect(jsonPath("$.dataUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.rooms[*].room", contains("C", "A")))
                .andExpect(jsonPath("$.rooms[0].building").value("LAS"))
                .andExpect(jsonPath("$.rooms[0].freeUntil").value("23:00"))
                .andExpect(jsonPath("$.rooms[0].freeUntilReason").value("CLOSING"))
                .andExpect(jsonPath("$.rooms[0].minutesFree").value(795))
                .andExpect(jsonPath("$.rooms[1].building").value("CLH"))
                .andExpect(jsonPath("$.rooms[1].freeUntil").value("12:00"))
                .andExpect(jsonPath("$.rooms[1].minutesFree").value(135));
    }

    @Test
    void meetingsOutsideTheirDateRangeDoNotBlockRooms() throws Exception {
        // CLH A's 8:00-9:00 Monday class ended on 2026-10-30.
        free("?at=2026-11-09T08:15&building=CLH").andExpect(jsonPath("$.rooms[0].freeUntil").value("12:00"));
        free("?at=2026-10-26T08:15&building=CLH").andExpect(jsonPath("$.rooms", hasSize(0)));
    }

    @Test
    void instantsAreConvertedToCampusTime() throws Exception {
        free("?at=2026-11-09T14:45:00Z").andExpect(jsonPath("$.at").value("2026-11-09T09:45:00-05:00"));
    }

    @Test
    void buildingFilterIsCaseInsensitiveAndMinMinutesFilters() throws Exception {
        free("?at=2026-11-09T09:45&building=las").andExpect(jsonPath("$.rooms[*].room", contains("C")));
        free("?at=2026-11-09T09:45&minMinutes=200").andExpect(jsonPath("$.rooms", hasSize(1)));
    }

    @Test
    void readingWeekMeansEveryRoomIsFree() throws Exception {
        free("?at=2026-10-14T10:00")
                .andExpect(jsonPath("$.status").value("NO_CLASSES"))
                .andExpect(jsonPath("$.rooms", hasSize(4)))
                .andExpect(jsonPath("$.notice", containsString("Fall Reading Week")));
    }

    @Test
    void examsHolidaysSummerAndNightExplainInsteadOfGuessing() throws Exception {
        free("?at=2026-12-15T10:00").andExpect(jsonPath("$.status").value("EXAMS"))
                .andExpect(jsonPath("$.rooms", hasSize(0)))
                .andExpect(jsonPath("$.notice", containsString("doesn't apply during exams")));
        free("?at=2026-10-12T10:00").andExpect(jsonPath("$.status").value("CLOSED"));
        free("?at=2026-08-01T10:00").andExpect(jsonPath("$.status").value("OUTSIDE_TERM"));
        free("?at=2026-11-09T23:30").andExpect(jsonPath("$.status").value("OUTSIDE_HOURS"));
    }

    @Test
    void makeUpDayUsesTheFridayTimetable() throws Exception {
        // Monday 2027-04-05 runs Friday's timetable: CLH A has a Friday 9:00-12:00 class.
        free("?at=2027-04-05T10:00&building=CLH")
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.timetableDay").value("FRIDAY"))
                .andExpect(jsonPath("$.rooms", hasSize(0)));
    }

    @Test
    void roomScheduleShowsBookedAndFreeBlocks() throws Exception {
        mvc.perform(get("/api/rooms/" + roomId("LAS", "A") + "/schedule?date=2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.building").value("LAS"))
                .andExpect(jsonPath("$.room").value("A"))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.booked[*].start", contains("08:30", "13:00")))
                .andExpect(jsonPath("$.booked[*].end", contains("11:30", "14:00")))
                .andExpect(jsonPath("$.free[*].start", contains("07:00", "11:30", "14:00")))
                .andExpect(jsonPath("$.free[*].end", contains("08:30", "13:00", "23:00")));
    }

    @Test
    void courseDetailsNeverLeaveTheServer() throws Exception {
        String free = free("?at=2026-11-09T09:45").andReturn().getResponse().getContentAsString();
        String schedule = mvc.perform(get("/api/rooms/" + roomId("LAS", "A") + "/schedule?date=2026-11-09"))
                .andReturn().getResponse().getContentAsString();
        assertThat(free + schedule).doesNotContain("SECRET").doesNotContainIgnoringCase("course").doesNotContain("LECT");
    }

    @Test
    void defaultsToNowWithoutParameters() throws Exception {
        free("").andExpect(status().isOk()).andExpect(jsonPath("$.status").isNotEmpty());
        mvc.perform(get("/api/rooms/" + roomId("LAS", "A") + "/schedule")).andExpect(status().isOk());
    }

    @Test
    void errorsAreProblemDetails() throws Exception {
        free("?minMinutes=-1")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].parameter").value("minMinutes"));
        free("?at=next-tuesday")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].parameter").value("at"));
        free("?building=L%24S").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("building")));
        free("?building=NOPE")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unknown building 'NOPE'"));
        mvc.perform(get("/api/rooms/999999/schedule")).andExpect(status().isNotFound());
        mvc.perform(get("/api/rooms/abc/schedule")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/rooms/0/schedule")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rooms/1/schedule?date=2026-13-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/nope")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
