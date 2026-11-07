package dev.yuroom.api;

import dev.yuroom.api.ApiDtos.BuildingDto;
import dev.yuroom.availability.AvailabilityService;
import dev.yuroom.buildings.BuildingDirectory;
import dev.yuroom.buildings.BuildingDirectory.BuildingInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buildings")
class BuildingController {

    private final AvailabilityService service;
    private final BuildingDirectory directory;

    BuildingController(AvailabilityService service, BuildingDirectory directory) {
        this.service = service;
        this.directory = directory;
    }

    /** Buildings that have rooms in the timetable, enriched with curated names and coordinates. */
    @GetMapping
    List<BuildingDto> list() {
        return service.buildings().stream().map(s -> {
            Optional<BuildingInfo> info = directory.find(s.building().getCode());
            return new BuildingDto(
                    s.building().getCode(),
                    info.map(BuildingInfo::name).orElse(s.building().getName()),
                    s.building().getCampus() != null ? s.building().getCampus() : info.map(BuildingInfo::campus).orElse(null),
                    info.map(BuildingInfo::latitude).orElse(null),
                    info.map(BuildingInfo::longitude).orElse(null),
                    s.rooms());
        }).toList();
    }
}
