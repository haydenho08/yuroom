package dev.yuroom.buildings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildingDirectoryTest {

    private static BuildingDirectory bundled() throws IOException {
        try (Reader in = new InputStreamReader(
                BuildingDirectoryTest.class.getResourceAsStream("/buildings/buildings.csv"), StandardCharsets.UTF_8)) {
            return BuildingDirectory.read(in);
        }
    }

    @Test
    void bundledFileLoadsWithQuotedNames() throws IOException {
        BuildingDirectory dir = bundled();
        assertThat(dir.size()).isEqualTo(29);
        assertThat(dir.find("hne")).get().extracting(BuildingDirectory.BuildingInfo::name)
                .isEqualTo("Health, Nursing and Environmental Studies");
        assertThat(dir.find("NOPE")).isEmpty();
    }

    @Test
    void keeleBuildingsAreOnKeeleCampus() throws IOException {
        // Sanity check on hand-copied coordinates: everything tagged Keele is within ~1.5 km of Vari Hall.
        BuildingDirectory dir = bundled();
        var vh = dir.find("VH").orElseThrow();
        for (String code : new String[] {"LAS", "CLH", "ACW", "DB", "R", "BC", "SC", "LSB", "WC", "SSB"}) {
            var b = dir.find(code).orElseThrow();
            assertThat(b.campus()).isEqualTo("Keele");
            assertThat(Math.abs(b.latitude() - vh.latitude())).as(code).isLessThan(0.012);
            assertThat(Math.abs(b.longitude() - vh.longitude())).as(code).isLessThan(0.015);
        }
        assertThat(dir.find("YH").orElseThrow().campus()).isEqualTo("Glendon");
    }

    @Test
    void rejectsDuplicatesAndBadCoordinates() {
        String dup = "code,name,campus,latitude,longitude\nLAS,A,Keele,43.7,-79.5\nlas,B,Keele,43.7,-79.5\n";
        assertThatThrownBy(() -> BuildingDirectory.read(new StringReader(dup))).hasMessageContaining("duplicate code LAS");
        String bad = "code,name,campus,latitude,longitude\nLAS,A,Keele,143.7,-79.5\n";
        assertThatThrownBy(() -> BuildingDirectory.read(new StringReader(bad))).hasMessageContaining("out of range");
    }
}
