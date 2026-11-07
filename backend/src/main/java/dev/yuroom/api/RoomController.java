package dev.yuroom.api;

import static dev.yuroom.api.ApiDtos.hm;

import dev.yuroom.api.ApiDtos.FreeRoomDto;
import dev.yuroom.api.ApiDtos.FreeRoomsResponse;
import dev.yuroom.api.ApiDtos.HoursDto;
import dev.yuroom.api.ApiDtos.RangeDto;
import dev.yuroom.api.ApiDtos.RoomScheduleResponse;
import dev.yuroom.availability.AvailabilityService;
import dev.yuroom.availability.AvailabilityService.FreeRoomsAnswer;
import dev.yuroom.availability.AvailabilityService.RoomDay;
import dev.yuroom.availability.core.CampusTime;
import dev.yuroom.availability.core.DayContext;
import dev.yuroom.availability.core.FreeTimeCalculator;
import dev.yuroom.availability.core.RoomAvailability;
import dev.yuroom.availability.core.RoomAvailability.DaySchedule;
import dev.yuroom.availability.core.RoomAvailability.FreeRoomsResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP only: parse and validate input, call the service, map to DTOs. No business logic here. */
@RestController
@RequestMapping("/api/rooms")
class RoomController {

    private final AvailabilityService service;
    private final FreeTimeCalculator calc;
    private final Clock clock;

    RoomController(AvailabilityService service, RoomAvailability availability, Clock clock) {
        this.service = service;
        this.calc = availability.calculator();
        this.clock = clock;
    }

    /**
     * Rooms with no class at {@code at} (default: now), longest-free first.
     *
     * @param minMinutes only rooms free for at least this long (0-1440)
     */
    @GetMapping("/free")
    FreeRoomsResponse free(
            @RequestParam(required = false)
            @Pattern(regexp = "[A-Za-z0-9]{1,16}", message = "must be a building code like LAS") String building,
            @RequestParam(required = false) String at,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1440) int minMinutes) {

        ZonedDateTime when = parseAtOrNow(at);
        FreeRoomsAnswer answer = service.freeRooms(
                Optional.ofNullable(building).map(b -> b.toUpperCase(Locale.ROOT)), when, minMinutes);
        FreeRoomsResult r = answer.result();
        return new FreeRoomsResponse(when.toOffsetDateTime(), when.toLocalDate(), r.status().name(), r.notice(),
                r.day().termCode(), timetableDay(r.day()), hours(), answer.dataUpdatedAt(),
                r.rooms().stream().map(f -> new FreeRoomDto(f.room().id(), f.room().building(), f.room().number(),
                        hm(f.until()), f.reason().name(), f.minutes())).toList());
    }

    /** One room's bookings and free gaps for a day (default: today). Never includes course details. */
    @GetMapping("/{id}/schedule")
    RoomScheduleResponse schedule(
            @PathVariable @Positive long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate day = date != null ? date : CampusTime.toCampus(clock.instant()).toLocalDate();
        RoomDay rd = service.roomSchedule(id, day);
        DaySchedule s = rd.schedule();
        return new RoomScheduleResponse(rd.room().getId(), rd.room().getBuilding().getCode(), rd.room().getNumber(),
                day, s.status().name(), s.notice(), s.day().termCode(), timetableDay(s.day()), hours(),
                s.booked().stream().map(RangeDto::of).toList(), s.free().stream().map(RangeDto::of).toList(),
                rd.dataUpdatedAt());
    }

    private ZonedDateTime parseAtOrNow(String at) {
        if (at == null || at.isBlank()) {
            return CampusTime.toCampus(clock.instant());
        }
        try {
            return CampusTime.parseAt(at);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("at", e.getMessage());
        }
    }

    private HoursDto hours() {
        return new HoursDto(hm(calc.open()), hm(calc.close()));
    }

    private static String timetableDay(DayContext day) {
        return day.timetableApplies() ? day.effectiveDay().name() : null;
    }
}
