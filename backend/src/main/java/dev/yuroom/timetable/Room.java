package dev.yuroom.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/** A bookable room. Natural key: (building, number). */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(nullable = false, length = 16)
    private String number;

    protected Room() {
        // for JPA
    }

    public Room(Building building, String number) {
        this.building = Objects.requireNonNull(building);
        this.number = Objects.requireNonNull(number);
    }

    public Long getId() {
        return id;
    }

    public Building getBuilding() {
        return building;
    }

    public String getNumber() {
        return number;
    }
}
