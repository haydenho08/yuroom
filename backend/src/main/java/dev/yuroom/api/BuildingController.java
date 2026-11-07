package dev.yuroom.api;

import dev.yuroom.api.ApiDtos.BuildingDto;
import dev.yuroom.availability.AvailabilityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buildings")
class BuildingController {

    private final AvailabilityService service;

    BuildingController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    List<BuildingDto> list() {
        return service.buildings().stream()
                .map(s -> new BuildingDto(s.building().getCode(), s.building().getName(), s.building().getCampus(), s.rooms()))
                .toList();
    }
}
