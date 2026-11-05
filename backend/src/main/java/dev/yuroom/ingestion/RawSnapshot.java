package dev.yuroom.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The exact bytes we collected, kept so any parse can be reproduced or redone later. */
@Entity
@Table(name = "raw_snapshot")
public class RawSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(length = 256)
    private String etag;

    @Column(name = "last_modified", length = 64)
    private String lastModified;

    protected RawSnapshot() {
        // for JPA
    }

    public RawSnapshot(String source, Instant fetchedAt, String contentSha256, byte[] content,
                       String contentType, String etag, String lastModified) {
        this.source = source;
        this.fetchedAt = fetchedAt;
        this.contentSha256 = contentSha256;
        this.content = content.clone();
        this.contentType = contentType;
        this.etag = etag;
        this.lastModified = lastModified;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public byte[] getContent() {
        return content.clone();
    }

    public String getEtag() {
        return etag;
    }

    public String getLastModified() {
        return lastModified;
    }
}
