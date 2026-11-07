package dev.yuroom.availability;

import dev.yuroom.availability.core.AcademicCalendar;
import dev.yuroom.availability.core.DayContext;
import dev.yuroom.availability.core.RoomAvailability;
import dev.yuroom.availability.core.RoomAvailability.DaySchedule;
import dev.yuroom.availability.core.RoomAvailability.FreeRoomsResult;
import dev.yuroom.availability.core.RoomAvailability.RoomRef;
import dev.yuroom.availability.core.TermSpan;
import dev.yuroom.availability.core.TimeRange;
import dev.yuroom.ingestion.IngestionRun;
import dev.yuroom.ingestion.IngestionRunRepository;
import dev.yuroom.ingestion.RunStatus;
import dev.yuroom.timetable.Building;
import dev.yuroom.timetable.BuildingRepository;
import dev.yuroom.timetable.MeetingRepository;
import dev.yuroom.timetable.MeetingSlot;
import dev.yuroom.timetable.Room;
import dev.yuroom.timetable.RoomRepository;
import dev.yuroom.timetable.TermRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads exactly the data a question needs, hands it to the pure core, returns the answer.
 * Computed on every request (no precomputed "free slots" table): see docs/stage-2.md.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    /** Answer to a free-rooms question, plus the context the API reports. */
    public record FreeRoomsAnswer(ZonedDateTime at, FreeRoomsResult result, Instant dataUpdatedAt) {
    }

    public record RoomDay(Room room, LocalDate date, DaySchedule schedule, Instant dataUpdatedAt) {
    }

    public record BuildingSummary(Building building, long rooms) {
    }

    private final RoomAvailability availability;
    private final AcademicCalendar calendar;
    private final TermRepository terms;
    private final BuildingRepository buildings;
    private final RoomRepository rooms;
    private final MeetingRepository meetings;
    private final IngestionRunRepository runs;

    public AvailabilityService(RoomAvailability availability, AcademicCalendar calendar, TermRepository terms,
                               BuildingRepository buildings, RoomRepository rooms, MeetingRepository meetings,
                               IngestionRunRepository runs) {
        this.availability = availability;
        this.calendar = calendar;
        this.terms = terms;
        this.buildings = buildings;
        this.rooms = rooms;
        this.meetings = meetings;
        this.runs = runs;
    }

    public List<BuildingSummary> buildings() {
        Map<Long, Long> counts = rooms.countByBuilding().stream().collect(
                Collectors.toMap(RoomRepository.BuildingRoomCount::getBuildingId, RoomRepository.BuildingRoomCount::getRooms));
        return buildings.findAllByOrderByCodeAsc().stream()
                .map(b -> new BuildingSummary(b, counts.getOrDefault(b.getId(), 0L)))
                .toList();
    }

    public FreeRoomsAnswer freeRooms(Optional<String> buildingCode, ZonedDateTime at, int minMinutes) {
        LocalDate date = at.toLocalDate();
        Optional<Building> building = buildingCode.map(code -> buildings.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Unknown building '" + code + "'")));

        DayContext day = calendar.classify(date, termSpans());
        List<Room> candidateRooms = building.map(b -> rooms.findByBuildingIdWithBuilding(b.getId()))
                .orElseGet(rooms::findAllWithBuilding);

        Map<Long, List<TimeRange>> bookings = Map.of();
        if (day.timetableApplies()) { // don't hit the meeting table when the answer doesn't depend on it
            List<MeetingSlot> slots = building
                    .map(b -> meetings.findSlotsOnInBuilding(day.effectiveDay(), date, b.getId()))
                    .orElseGet(() -> meetings.findSlotsOn(day.effectiveDay(), date));
            bookings = slots.stream().collect(Collectors.groupingBy(MeetingSlot::roomId,
                    Collectors.mapping(s -> new TimeRange(s.start(), s.end()), Collectors.toList())));
        }

        FreeRoomsResult result = availability.freeRooms(date, at.toLocalTime(), day,
                candidateRooms.stream().map(AvailabilityService::ref).toList(), bookings, minMinutes);
        return new FreeRoomsAnswer(at, result, dataUpdatedAt());
    }

    public RoomDay roomSchedule(long roomId, LocalDate date) {
        Room room = rooms.findByIdWithBuilding(roomId)
                .orElseThrow(() -> new NotFoundException("No room with id " + roomId));
        DayContext day = calendar.classify(date, termSpans());
        List<TimeRange> booked = day.timetableApplies()
                ? meetings.findSlotsForRoom(roomId, day.effectiveDay(), date).stream()
                        .map(s -> new TimeRange(s.start(), s.end())).toList()
                : List.of();
        return new RoomDay(room, date, availability.daySchedule(date, day, booked), dataUpdatedAt());
    }

    private List<TermSpan> termSpans() {
        return terms.findAll().stream().map(t -> new TermSpan(t.getCode(), t.getStartDate(), t.getEndDate())).toList();
    }

    /** When the timetable was last confirmed current, so clients can show "updated 3h ago". */
    private Instant dataUpdatedAt() {
        return runs.findFirstByStatusInOrderByStartedAtDesc(EnumSet.of(RunStatus.SUCCESS, RunStatus.UNCHANGED))
                .map(IngestionRun::getFinishedAt).orElse(null);
    }

    private static RoomRef ref(Room r) {
        return new RoomRef(r.getId(), r.getBuilding().getCode(), r.getNumber());
    }
}
