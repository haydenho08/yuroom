package dev.yuroom.buildings;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Hand-curated facts about buildings that the timetable doesn't carry: full name, campus, and
 * coordinates for "near me". Public reference data, loaded once from a versioned file; pure Java.
 * Coordinates are public facts about buildings. They're sent to every client, which does the
 * distance maths locally, so the server never needs anyone's location.
 */
public final class BuildingDirectory {

    public record BuildingInfo(String code, String name, String campus, double latitude, double longitude) {
        public BuildingInfo {
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException(code + ": coordinates out of range");
            }
        }
    }

    private final Map<String, BuildingInfo> byCode;

    public BuildingDirectory(Map<String, BuildingInfo> byCode) {
        this.byCode = Map.copyOf(byCode);
    }

    public Optional<BuildingInfo> find(String code) {
        return Optional.ofNullable(byCode.get(code.toUpperCase(Locale.ROOT)));
    }

    public int size() {
        return byCode.size();
    }

    public static BuildingDirectory read(Reader source) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setCommentMarker('#')
                .setIgnoreSurroundingSpaces(true).setIgnoreEmptyLines(true).get();
        Map<String, BuildingInfo> result = new LinkedHashMap<>();
        try (CSVParser csv = CSVParser.parse(source, format)) {
            for (CSVRecord r : csv) {
                try {
                    String code = r.get("code").toUpperCase(Locale.ROOT);
                    BuildingInfo info = new BuildingInfo(code, r.get("name"), r.get("campus"),
                            Double.parseDouble(r.get("latitude")), Double.parseDouble(r.get("longitude")));
                    if (result.put(code, info) != null) {
                        throw new IllegalArgumentException("duplicate code " + code);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("buildings line " + csv.getCurrentLineNumber() + ": "
                            + e.getMessage(), e);
                }
            }
        }
        return new BuildingDirectory(result);
    }
}
