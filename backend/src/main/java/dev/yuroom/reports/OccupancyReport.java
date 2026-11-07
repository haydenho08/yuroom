package dev.yuroom.reports;

import dev.yuroom.timetable.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** "Someone is using this room right now." Anonymous by construction: no reporter fields exist. */
@Entity
@Table(name = "occupancy_report")
public class OccupancyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected OccupancyReport() {
        // for JPA
    }

    public OccupancyReport(Room room, Instant createdAt, Instant expiresAt) {
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("report must expire after it is created");
        }
        this.room = room;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
