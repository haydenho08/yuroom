package dev.yuroom.ingestion;

import dev.yuroom.ingestion.parse.CsvTimetableParser;
import dev.yuroom.ingestion.parse.TimetableParser;
import dev.yuroom.ingestion.source.FileTimetableSource;
import dev.yuroom.ingestion.source.HostRateLimiter;
import dev.yuroom.ingestion.source.HttpTimetableSource;
import dev.yuroom.ingestion.source.TimetableSource;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the source and parser from configuration. Swapping sources is a config change. */
@Configuration
class IngestionConfig {

    @Bean
    TimetableSource timetableSource(IngestionProperties props, Clock clock) {
        return switch (props.source()) {
            case FILE -> new FileTimetableSource(props.file().asPath());
            case HTTP -> {
                IngestionProperties.HttpSource http = props.http();
                if (http.url() == null || http.url().toString().isBlank()) {
                    throw new IllegalStateException("yuroom.ingestion.source=HTTP requires yuroom.ingestion.http.url");
                }
                yield new HttpTimetableSource(http.url(), http.userAgent(),
                        HttpTimetableSource.defaultClient(http.timeout()),
                        HostRateLimiter.realTime(http.minRequestInterval()), http.timeout(), clock);
            }
        };
    }

    @Bean
    TimetableParser timetableParser(IngestionProperties props) {
        return new CsvTimetableParser(props.charset());
    }
}
