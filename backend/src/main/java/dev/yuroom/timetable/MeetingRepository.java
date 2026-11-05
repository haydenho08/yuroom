package dev.yuroom.timetable;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    /** Bulk delete in one SQL statement (deleteAll would load every entity first). */
    @Modifying(flushAutomatically = true)
    @Query("delete from Meeting m where m.term in :terms")
    int deleteByTermIn(Collection<Term> terms);

    long countByTerm(Term term);
}
