package dev.yuroom.ingestion.source;

/** HTTP cache validators remembered from the last fetch. Either may be null. */
public record CacheValidators(String etag, String lastModified) {

    private static final CacheValidators NONE = new CacheValidators(null, null);

    public static CacheValidators none() {
        return NONE;
    }
}
