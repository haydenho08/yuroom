package dev.yuroom.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    /** Bulk delete in one SQL statement (deleteAll would load every entity first). */
    @Modifying(flushAutomatically = true)
    @Query("delete from Meeting m where m.term in :terms")
    int deleteByTermIn(Collection<Term> terms);

    long countByTerm(Term term);

    /** All bookings on one weekday that are in effect on {@code date}. Uses meeting_day_dates_idx. */
    @Query("""
            select new dev.yuroom.timetable.MeetingSlot(m.room.id, m.startTime, m.endTime)
            from Meeting m
            where m.dayOfWeek = :day and m.startDate <= :date and m.endDate >= :date""")
    List<MeetingSlot> findSlotsOn(@Param("day") DayOfWeek day, @Param("date") LocalDate date);

    /** Same, for one building. */
    @Query("""
            select new dev.yuroom.timetable.MeetingSlot(m.room.id, m.startTime, m.endTime)
            from Meeting m
            where m.dayOfWeek = :day and m.startDate <= :date and m.endDate >= :date
              and m.room.building.id = :buildingId""")
    List<MeetingSlot> findSlotsOnInBuilding(@Param("day") DayOfWeek day, @Param("date") LocalDate date,
                                            @Param("buildingId") Long buildingId);

    /** One room's bookings for a day. Uses meeting_room_day_idx. */
    @Query("""
            select new dev.yuroom.timetable.MeetingSlot(m.room.id, m.startTime, m.endTime)
            from Meeting m
            where m.room.id = :roomId and m.dayOfWeek = :day and m.startDate <= :date and m.endDate >= :date""")
    List<MeetingSlot> findSlotsForRoom(@Param("roomId") Long roomId, @Param("day") DayOfWeek day,
                                       @Param("date") LocalDate date);
}
