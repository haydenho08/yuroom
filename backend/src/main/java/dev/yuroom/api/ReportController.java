package dev.yuroom.api;

import dev.yuroom.reports.ReportService;
import dev.yuroom.reports.ReportService.SubmitResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/rooms/{id}/reports: "this room is actually occupied". No body, no login, no
 * personal data. The client address is used only to derive an in-memory rate-limit key.
 */
@RestController
@RequestMapping("/api/rooms/{id}/reports")
class ReportController {

    record ReportCreated(long roomId, Instant expiresAt, long activeReports, Instant lastReportedAt) {
    }

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    ResponseEntity<?> report(@PathVariable @Positive long id, HttpServletRequest request) {
        return switch (reports.submit(id, request.getRemoteAddr())) {
            case SubmitResult.Accepted a -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ReportCreated(a.roomId(), a.expiresAt(), a.active().reports(), a.active().latest()));
            case SubmitResult.RateLimited r -> {
                long seconds = Math.max(1, (r.retryAfter().toMillis() + 999) / 1000);
                ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, r.reason());
                p.setTitle("Too many reports");
                yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                        .body(p);
            }
        };
    }
}
