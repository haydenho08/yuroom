package dev.yuroom.timetable;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /** One query instead of 1 + N lazy building lookups. */
    @Query("select r from Room r join fetch r.building")
    List<Room> findAllWithBuilding();

    @Query("select r from Room r join fetch r.building b where b.id = :buildingId")
    List<Room> findByBuildingIdWithBuilding(Long buildingId);

    @Query("select r from Room r join fetch r.building where r.id = :id")
    Optional<Room> findByIdWithBuilding(Long id);

    /** Room count per building, for the building list. */
    @Query("select r.building.id as buildingId, count(r) as rooms from Room r group by r.building.id")
    List<BuildingRoomCount> countByBuilding();

    interface BuildingRoomCount {
        Long getBuildingId();

        Long getRooms();
    }
}
