package dev.yuroom.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/** An academic term, e.g. code "2026FA". Dates span the first to last class meeting. */
@Entity
@Table(name = "term")
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected Term() {
        // for JPA
    }

    public Term(String code, LocalDate startDate, LocalDate endDate) {
        this.code = Objects.requireNonNull(code);
        checkDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void setDates(LocalDate startDate, LocalDate endDate) {
        checkDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    private static void checkDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("term end " + endDate + " is before start " + startDate);
        }
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
