package dev.yuroom.timetable;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByCode(String code);

    List<Building> findAllByOrderByCodeAsc();
}
