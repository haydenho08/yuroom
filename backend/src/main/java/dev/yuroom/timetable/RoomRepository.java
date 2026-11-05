package dev.yuroom.timetable;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /** One query instead of 1 + N lazy building lookups. */
    @Query("select r from Room r join fetch r.building")
    List<Room> findAllWithBuilding();
}
