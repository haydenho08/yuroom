package dev.yuroom.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class IngestionPropertiesTest {

    private static IngestionProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("yuroom.ingestion", IngestionProperties.class);
    }

    @Test
    void defaultsArePoliteAndSafe() {
        IngestionProperties props = bind(Map.of());
        assertThat(props.source()).isEqualTo(IngestionProperties.SourceType.FILE);
        assertThat(props.minInterval()).isEqualTo(Duration.ofHours(20));
        assertThat(props.runOnStartup()).isFalse();
        assertThat(props.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(props.http().userAgent()).isEqualTo("YURoom student project");
        assertThat(props.http().minRequestInterval()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void relativeFilePathsBind() {
        // Regression: binding straight to java.nio.file.Path rejected "../" at app startup.
        IngestionProperties props = bind(Map.of("yuroom.ingestion.file.path", "../data/sample/x.csv"));
        assertThat(props.file().asPath()).isEqualTo(Path.of("../data/sample/x.csv"));
    }
}
