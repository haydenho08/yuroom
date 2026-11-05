package dev.yuroom.ingestion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawSnapshotRepository extends JpaRepository<RawSnapshot, Long> {

    /** Metadata only. An interface projection selects these columns and skips the (large) content. */
    interface SnapshotMeta {
        Long getId();

        String getContentSha256();

        String getEtag();

        String getLastModified();
    }

    Optional<SnapshotMeta> findMetaById(Long id);

    Optional<SnapshotMeta> findMetaBySourceAndContentSha256(String source, String contentSha256);
}
