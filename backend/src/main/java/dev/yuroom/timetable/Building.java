package dev.yuroom.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/** A campus building, identified by its acronym (e.g. "LAS"). */
@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(length = 128)
    private String name;

    @Column(length = 32)
    private String campus;

    protected Building() {
        // for JPA
    }

    public Building(String code, String campus) {
        this.code = Objects.requireNonNull(code);
        this.campus = campus;
    }

    /** Fills in the campus the first time a data source tells us; never overwrites. */
    public void learnCampus(String campus) {
        if (this.campus == null && campus != null) {
            this.campus = campus;
        }
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCampus() {
        return campus;
    }
}
