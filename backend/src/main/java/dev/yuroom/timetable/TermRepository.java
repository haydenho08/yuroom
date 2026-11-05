package dev.yuroom.timetable;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

    Optional<Term> findByCode(String code);
}
