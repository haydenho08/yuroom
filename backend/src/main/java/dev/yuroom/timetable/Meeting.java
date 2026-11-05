package dev.yuroom.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One weekly recurring class meeting: a room is booked on {@code dayOfWeek} from
 * {@code startTime} to {@code endTime}, every week between {@code startDate} and {@code endDate}.
 *
 * <p>Times are local wall-clock times in America/Toronto. We deliberately store
 * {@link LocalTime}, not instants: "LECT at 10:00" means 10:00 on both sides of a DST change.
 *
 * <p>{@code courseCode}/{@code section} are kept for debugging and data quality only; the
 * public API will not expose them (see docs/stage-0.md).
 */
@Entity
@Table(name = "meeting")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "meeting_seq")
    @SequenceGenerator(name = "meeting_seq", sequenceName = "meeting_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "course_code", length = 32)
    private String courseCode;

    @Column(length = 16)
    private String section;

    @Column(name = "meeting_type", length = 8)
    private String meetingType;

    protected Meeting() {
        // for JPA
    }

    public Meeting(Term term, Room room, Long snapshotId, DayOfWeek dayOfWeek,
                   LocalTime startTime, LocalTime endTime, LocalDate startDate, LocalDate endDate,
                   String courseCode, String section, String meetingType) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("meeting must end after it starts");
        }
        this.term = term;
        this.room = room;
        this.snapshotId = snapshotId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startDate = startDate;
        this.endDate = endDate;
        this.courseCode = courseCode;
        this.section = section;
        this.meetingType = meetingType;
    }

    public Long getId() {
        return id;
    }

    public Term getTerm() {
        return term;
    }

    public Room getRoom() {
        return room;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getSection() {
        return section;
    }

    public String getMeetingType() {
        return meetingType;
    }
}
