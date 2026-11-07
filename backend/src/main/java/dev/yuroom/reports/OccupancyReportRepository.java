package dev.yuroom.reports;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OccupancyReportRepository extends JpaRepository<OccupancyReport, Long> {

    /**
     * Reports in effect at {@code at} (created at or before it, not yet expired), counted per room.
     * Uses occupancy_report_active_idx.
     */
    @Query("""
            select r.room.id as roomId, count(r) as reports, max(r.createdAt) as latest
            from OccupancyReport r where r.expiresAt > :at and r.createdAt <= :at group by r.room.id""")
    List<ActiveReports> findActiveAt(Instant at);

    @Query("""
            select r.room.id as roomId, count(r) as reports, max(r.createdAt) as latest
            from OccupancyReport r
            where r.expiresAt > :at and r.createdAt <= :at and r.room.id = :roomId group by r.room.id""")
    List<ActiveReports> findActiveAtForRoom(Long roomId, Instant at);

    @Modifying
    @Query("delete from OccupancyReport r where r.expiresAt <= :now")
    int deleteExpired(Instant now);

    interface ActiveReports {
        Long getRoomId();

        Long getReports();

        Instant getLatest();
    }
}
