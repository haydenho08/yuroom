package dev.yuroom.ingestion.source;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches a timetable over HTTP, politely. <b>Only point this at a URL we have permission
 * to use</b> (see docs/stage-0.md); York's public courses site is not one of them.
 *
 * <p>Politeness rules, in order:
 * <ol>
 *   <li><b>robots.txt first</b>, cached for 24h. If robots.txt can't be read we assume "no":
 *       404/410 means "no rules" (allow, per RFC 9309), but 401/403, 429, 5xx and network
 *       errors all mean "don't crawl". A bot challenge in front of robots.txt is a 403.</li>
 *   <li><b>Identify ourselves</b> with a generic User-Agent, never a personal email.</li>
 *   <li><b>Rate limit</b> every request (robots.txt included) per host.</li>
 *   <li><b>Conditional GET</b> with ETag / Last-Modified, so an unchanged file costs a 304.</li>
 *   <li><b>Back off</b> on 429/503 instead of retrying.</li>
 *   <li><b>No redirects</b> followed silently: a redirect could lead to a host whose
 *       robots.txt we haven't checked.</li>
 * </ol>
 */
public final class HttpTimetableSource implements TimetableSource {

    private static final Logger log = LoggerFactory.getLogger(HttpTimetableSource.class);
    static final Duration ROBOTS_TTL = Duration.ofHours(24);

    private final URI url;
    private final String userAgent;
    private final HttpClient client;
    private final HostRateLimiter rateLimiter;
    private final Duration timeout;
    private final Clock clock;

    private RobotsTxt robots;
    private Instant robotsFetchedAt;

    public HttpTimetableSource(URI url, String userAgent, HttpClient client, HostRateLimiter rateLimiter,
                               Duration timeout, Clock clock) {
        this.url = Objects.requireNonNull(url);
        if (url.getHost() == null || !(url.getScheme().equals("https") || url.getScheme().equals("http"))) {
            throw new IllegalArgumentException("not an http(s) URL: " + url);
        }
        this.userAgent = Objects.requireNonNull(userAgent);
        this.client = Objects.requireNonNull(client);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.timeout = Objects.requireNonNull(timeout);
        this.clock = Objects.requireNonNull(clock);
    }

    public static HttpClient defaultClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String id() {
        return "http:" + url.getHost();
    }

    @Override
    public synchronized FetchResult fetch(CacheValidators previous) throws IOException, InterruptedException {
        String pathAndQuery = pathAndQuery(url);
        if (!robots().isAllowed(userAgent, pathAndQuery)) {
            return new FetchResult.Blocked("robots.txt does not allow " + pathAndQuery);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(url).timeout(timeout).GET()
                .header("User-Agent", userAgent);
        if (previous.etag() != null) {
            request.header("If-None-Match", previous.etag());
        }
        if (previous.lastModified() != null) {
            request.header("If-Modified-Since", previous.lastModified());
        }

        rateLimiter.acquire(url.getHost());
        HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        return switch (status) {
            case 200 -> new FetchResult.Fetched(response.body(),
                    header(response, "Content-Type"), header(response, "ETag"), header(response, "Last-Modified"));
            case 304 -> new FetchResult.NotModified();
            case 401, 403 -> new FetchResult.Blocked("server refused access (HTTP " + status + ")");
            case 429, 503 -> new FetchResult.Blocked("server asked us to back off (HTTP " + status + ")");
            default -> throw new IOException("unexpected HTTP " + status + " from " + url);
        };
    }

    private RobotsTxt robots() throws InterruptedException {
        Instant now = clock.instant();
        if (robots != null && robotsFetchedAt.plus(ROBOTS_TTL).isAfter(now)) {
            return robots;
        }
        URI robotsUri = url.resolve("/robots.txt");
        RobotsTxt result;
        try {
            rateLimiter.acquire(url.getHost());
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(robotsUri).timeout(timeout).GET().header("User-Agent", userAgent).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                result = RobotsTxt.parse(response.body());
            } else if (status == 404 || status == 410) {
                result = RobotsTxt.allowAll();
            } else {
                log.warn("robots.txt at {} returned HTTP {}; treating as disallow-all", robotsUri, status);
                result = RobotsTxt.disallowAll();
            }
        } catch (IOException e) {
            log.warn("could not read robots.txt at {} ({}); treating as disallow-all", robotsUri, e.toString());
            result = RobotsTxt.disallowAll();
        }
        robots = result;
        robotsFetchedAt = now;
        return result;
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }
}
