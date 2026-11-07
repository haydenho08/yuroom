package dev.yuroom.ingestion;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    Optional<IngestionRun> findFirstBySourceOrderByStartedAtDesc(String source);

    Optional<IngestionRun> findFirstByStatusInOrderByStartedAtDesc(Collection<RunStatus> statuses);

    Optional<IngestionRun> findFirstBySourceAndStatusInOrderByStartedAtDesc(String source,
                                                                             Collection<RunStatus> statuses);
}
