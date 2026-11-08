package dev.yuroom.api;

import dev.yuroom.usage.UsageEvent;
import dev.yuroom.usage.UsageService;
import dev.yuroom.usage.WeeklyUsage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/usage {"events": ["visit", "daily_visitor"]}: anonymous counters, nothing else.
 * GET /api/usage/weekly?weeks=12: the totals, public on purpose. If the numbers go on a resume,
 * anyone can check them.
 */
@RestController
@RequestMapping("/api/usage")
class UsageController {

    record UsageRequest(@NotEmpty @Size(max = 4) List<String> events) {
    }

    record WeeklyUsageResponse(String timeZone, String note, List<WeeklyUsage.Week> weeks) {
    }

    static final String NOTE = "Counts are per browser, not per person: clearing site data or using several devices "
            + "counts again, private windows count every visit as new. No personal data is collected.";

    private final UsageService usage;

    UsageController(UsageService usage) {
        this.usage = usage;
    }

    @PostMapping
    ResponseEntity<?> record(@Valid @RequestBody UsageRequest body, HttpServletRequest request) {
        Set<UsageEvent> events = EnumSet.noneOf(UsageEvent.class);
        for (String name : body.events()) {
            UsageEvent e = UsageEvent.fromWire(name).filter(UsageEvent::clientMaySend)
                    .orElseThrow(() -> new BadRequestException("events", "unknown event '" + name + "'"));
            events.add(e);
        }
        if (!usage.recordFromClient(events, request.getRemoteAddr())) {
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many usage events.");
            p.setTitle("Too many requests");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(p);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/weekly")
    WeeklyUsageResponse weekly(@RequestParam(defaultValue = "12") @Min(1) @Max(52) int weeks) {
        return new WeeklyUsageResponse("America/Toronto", NOTE, usage.weekly(weeks));
    }
}
