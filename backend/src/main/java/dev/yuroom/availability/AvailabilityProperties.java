package dev.yuroom.availability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code yuroom.availability.*}. Hours are strings parsed by us (like the file path in Stage 1,
 * we don't rely on implicit framework conversion for values that decide correctness).
 */
@Validated
@ConfigurationProperties("yuroom.availability")
public record AvailabilityProperties(
        @DefaultValue("07:00") @Pattern(regexp = "\\d{2}:\\d{2}") String open,
        @DefaultValue("23:00") @Pattern(regexp = "\\d{2}:\\d{2}") String close,
        @DefaultValue("classpath:calendar/academic-calendar.csv") @NotBlank String calendar) {

    public LocalTime openTime() {
        return LocalTime.parse(open);
    }

    public LocalTime closeTime() {
        return LocalTime.parse(close);
    }
}
