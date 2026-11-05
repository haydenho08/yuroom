package dev.yuroom.timetable;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/**
 * Stores {@link DayOfWeek} as its ISO number (1 = Monday ... 7 = Sunday).
 * Safer than {@code @Enumerated(ORDINAL)}, whose 0-based numbering is an accident of
 * declaration order, and more compact/indexable than storing the name.
 */
@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek day) {
        return day == null ? null : (short) day.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short value) {
        return value == null ? null : DayOfWeek.of(value);
    }
}
