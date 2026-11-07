package dev.yuroom.reports;

import dev.yuroom.availability.NotFoundException;
import dev.yuroom.timetable.Room;
import dev.yuroom.timetable.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts anonymous occupancy reports and summarises the active ones.
 * Two limits, both in memory: one report per room per client per 30 min (no stacking reports on one
 * room), and 10 reports per client per hour (no sweeping a whole building).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    public record ActiveSummary(long reports, Instant latest) {
    }

    public sealed interface SubmitResult {
        record Accepted(long roomId, Instant expiresAt, ActiveSummary active) implements SubmitResult {
        }

        record RateLimited(Duration retryAfter, String reason) implements SubmitResult {
        }
    }

    private final OccupancyReportRepository reports;
    private final RoomRepository rooms;
    private final ReportProperties props;
    private final Clock clock;
    private final ClientKeys clientKeys;
    private final RateLimiter perRoom;
    private final RateLimiter perClient;

    public ReportService(OccupancyReportRepository reports, RoomRepository rooms, ReportProperties props, Clock clock) {
        this.reports = reports;
        this.rooms = rooms;
        this.props = props;
        this.clock = clock;
        this.clientKeys = new ClientKeys(clock, props.keyRotation());
        this.perRoom = new RateLimiter(1, props.perRoomWindow(), clock, props.maxTrackedClients());
        this.perClient = new RateLimiter(props.perClientLimit(), props.perClientWindow(), clock, props.maxTrackedClients());
    }

    @Transactional
    public SubmitResult submit(long roomId, String clientAddress) {
        Room room = rooms.findById(roomId).orElseThrow(() -> new NotFoundException("No room with id " + roomId));
        String client = clientKeys.keyFor(clientAddress);

        RateLimiter.Decision sameRoom = perRoom.tryAcquire(client + '|' + roomId);
        if (!sameRoom.allowed()) {
            return new SubmitResult.RateLimited(sameRoom.retryAfter(), "You already reported this room recently.");
        }
        RateLimiter.Decision overall = perClient.tryAcquire(client);
        if (!overall.allowed()) {
            return new SubmitResult.RateLimited(overall.retryAfter(), "Too many reports from this device. Try again later.");
        }

        Instant now = clock.instant();
        OccupancyReport saved = reports.save(new OccupancyReport(room, now, now.plus(props.ttl())));
        return new SubmitResult.Accepted(roomId, saved.getExpiresAt(), activeFor(roomId, now).orElseThrow());
    }

    /** Reports in effect at {@code at}, per room. A report made now says nothing about tomorrow. */
    @Transactional(readOnly = true)
    public Map<Long, ActiveSummary> activeAt(Instant at) {
        return reports.findActiveAt(at).stream().collect(Collectors.toMap(
                OccupancyReportRepository.ActiveReports::getRoomId, a -> new ActiveSummary(a.getReports(), a.getLatest())));
    }

    @Transactional(readOnly = true)
    public Optional<ActiveSummary> activeFor(long roomId, Instant at) {
        return reports.findActiveAtForRoom(roomId, at).stream()
                .findFirst().map(a -> new ActiveSummary(a.getReports(), a.getLatest()));
    }

    public Instant now() {
        return clock.instant();
    }

    /** Expired reports are already invisible; this just keeps the table and memory small. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public void cleanUp() {
        int deleted = reports.deleteExpired(clock.instant());
        int keys = perRoom.prune() + perClient.prune();
        if (deleted > 0 || keys > 0) {
            log.info("Report clean-up: deleted {} expired reports, dropped {} idle rate-limit keys", deleted, keys);
        }
    }
}
