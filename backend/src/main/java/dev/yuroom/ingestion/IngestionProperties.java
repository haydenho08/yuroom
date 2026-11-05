package dev.yuroom.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Typed, validated view of {@code yuroom.ingestion.*}. Bad config fails at startup, not at 4 a.m. */
@Validated
@ConfigurationProperties("yuroom.ingestion")
public record IngestionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("FILE") @NotNull SourceType source,
        @DefaultValue("0 15 4 * * *") @NotBlank String cron,
        @DefaultValue("20h") @NotNull Duration minInterval,
        @DefaultValue("false") boolean runOnStartup,
        @DefaultValue("0.2") @DecimalMin("0.0") @DecimalMax("1.0") double maxRejectRatio,
        @DefaultValue("UTF-8") @NotNull Charset charset,
        @DefaultValue @Valid FileSource file,
        @DefaultValue @Valid HttpSource http) {

    public enum SourceType { FILE, HTTP }

    public record FileSource(@DefaultValue("../data/sample/timetable-sample.csv") @NotBlank String path) {

        /** Plain String on purpose: Spring's String-to-Path conversion treats values as resource
         *  locations and rejects relative paths like "../data/...". */
        public Path asPath() {
            return Path.of(path);
        }
    }

    public record HttpSource(
            URI url,
            @DefaultValue("YURoom student project") @NotBlank String userAgent,
            @DefaultValue("10s") @NotNull Duration minRequestInterval,
            @DefaultValue("30s") @NotNull Duration timeout) {
    }
}
