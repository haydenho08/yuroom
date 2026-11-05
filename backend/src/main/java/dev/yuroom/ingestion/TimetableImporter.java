package dev.yuroom.ingestion;

import dev.yuroom.ingestion.parse.ParsedMeeting;
import dev.yuroom.timetable.Building;
import dev.yuroom.timetable.BuildingRepository;
import dev.yuroom.timetable.Meeting;
import dev.yuroom.timetable.MeetingRepository;
import dev.yuroom.timetable.Room;
import dev.yuroom.timetable.RoomRepository;
import dev.yuroom.timetable.Term;
import dev.yuroom.timetable.TermRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes parsed meetings into the database as a <b>full replacement</b> of each term present in
 * the file, inside one transaction. Readers see either the old timetable or the new one,
 * never a half-imported mix, and a failure rolls everything back.
 */
@Service
public class TimetableImporter {

    public record ImportStats(int terms, int newBuildings, int newRooms, int meetings) {
    }

    private final TermRepository terms;
    private final BuildingRepository buildings;
    private final RoomRepository rooms;
    private final MeetingRepository meetings;

    public TimetableImporter(TermRepository terms, BuildingRepository buildings, RoomRepository rooms,
                             MeetingRepository meetings) {
        this.terms = terms;
        this.buildings = buildings;
        this.rooms = rooms;
        this.meetings = meetings;
    }

    @Transactional
    public ImportStats replaceTimetable(List<ParsedMeeting> parsed, Long snapshotId) {
        // Reference data is small (~100 buildings, ~1-2k rooms): load once into maps
        // instead of one lookup query per row.
        Map<String, Building> buildingByCode = new HashMap<>();
        buildings.findAll().forEach(b -> buildingByCode.put(b.getCode(), b));
        Map<String, Room> roomByKey = new HashMap<>();
        rooms.findAllWithBuilding().forEach(r -> roomByKey.put(roomKey(r.getBuilding().getCode(), r.getNumber()), r));
        int buildingsBefore = buildingByCode.size();
        int roomsBefore = roomByKey.size();

        Map<String, Term> termByCode = upsertTerms(parsed);
        meetings.deleteByTermIn(termByCode.values());

        List<Meeting> toSave = new ArrayList<>(parsed.size());
        for (ParsedMeeting p : parsed) {
            Building building = buildingByCode.computeIfAbsent(p.buildingCode(),
                    code -> buildings.save(new Building(code, p.campus())));
            building.learnCampus(p.campus());
            Room room = roomByKey.computeIfAbsent(roomKey(p.buildingCode(), p.roomNumber()),
                    key -> rooms.save(new Room(building, p.roomNumber())));
            toSave.add(new Meeting(termByCode.get(p.termCode()), room, snapshotId, p.day(), p.start(), p.end(),
                    p.startDate(), p.endDate(), p.courseCode(), p.section(), p.meetingType()));
        }
        meetings.saveAll(toSave); // batched thanks to the sequence id + hibernate.jdbc.batch_size

        return new ImportStats(termByCode.size(), buildingByCode.size() - buildingsBefore,
                roomByKey.size() - roomsBefore, toSave.size());
    }

    /** A term's dates are the span of its meetings in the latest file. */
    private Map<String, Term> upsertTerms(List<ParsedMeeting> parsed) {
        Map<String, LocalDate[]> span = new LinkedHashMap<>();
        for (ParsedMeeting p : parsed) {
            span.merge(p.termCode(), new LocalDate[] {p.startDate(), p.endDate()}, (a, b) -> new LocalDate[] {
                    min(a[0], b[0]), max(a[1], b[1])});
        }
        Map<String, Term> result = new HashMap<>();
        span.forEach((code, range) -> {
            Term term = terms.findByCode(code).orElseGet(() -> new Term(code, range[0], range[1]));
            term.setDates(range[0], range[1]);
            result.put(code, terms.save(term));
        });
        return result;
    }

    private static String roomKey(String buildingCode, String roomNumber) {
        return buildingCode + '|' + roomNumber;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
