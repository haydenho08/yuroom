package dev.yuroom.ingestion.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the polite fetcher against a throwaway local HTTP server (JDK built-in),
 * so we test real HTTP behaviour without touching any real website.
 */
class HttpTimetableSourceTest {

    private record Response(int status, String body, Map<String, String> headers) {
    }

    private HttpServer server;
    private final Map<String, Response> routes = new ConcurrentHashMap<>();
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final List<String> ifNoneMatch = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedPaths.add(path);
        userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        String inm = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (inm != null) {
            ifNoneMatch.add(inm);
        }
        Response response = routes.getOrDefault(path, new Response(404, "", Map.of()));
        if (path.equals("/timetable.csv") && inm != null && inm.equals(response.headers().get("ETag"))) {
            response = new Response(304, "", Map.of());
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        response.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        exchange.sendResponseHeaders(response.status(), response.status() == 304 || body.length == 0 ? -1 : body.length);
        if (body.length > 0 && response.status() != 304) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private HttpTimetableSource source() {
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/timetable.csv");
        return new HttpTimetableSource(url, "YURoom student project",
                HttpTimetableSource.defaultClient(Duration.ofSeconds(5)),
                HostRateLimiter.realTime(Duration.ZERO), Duration.ofSeconds(5), Clock.systemUTC());
    }

    @Test
    void fetchesWhenRobotsMissingAndSendsGenericUserAgent() throws Exception {
        routes.put("/timetable.csv", new Response(200, "a,b\n", Map.of("ETag", "\"v1\"", "Content-Type", "text/csv")));

        FetchResult result = source().fetch(CacheValidators.none());

        assertThat(result).isInstanceOf(FetchResult.Fetched.class);
        FetchResult.Fetched fetched = (FetchResult.Fetched) result;
        assertThat(new String(fetched.body(), StandardCharsets.UTF_8)).isEqualTo("a,b\n");
        assertThat(fetched.etag()).isEqualTo("\"v1\"");
        assertThat(requestedPaths).containsExactly("/robots.txt", "/timetable.csv");
        assertThat(userAgents).containsOnly("YURoom student project");
    }

    @Test
    void robotsDisallowMeansTheTimetableIsNeverRequested() throws Exception {
        routes.put("/robots.txt", new Response(200, "User-agent: *\nDisallow: /\n", Map.of()));
        routes.put("/timetable.csv", new Response(200, "a,b\n", Map.of()));

        FetchResult result = source().fetch(CacheValidators.none());

        assertThat(result).isInstanceOf(FetchResult.Blocked.class);
        assertThat(requestedPaths).containsExactly("/robots.txt");
    }

    @Test
    void forbiddenRobotsTxtIsTreatedAsDisallowAll() throws Exception {
        // This is what York's Cloudflare-protected courses site does to non-browser clients.
        routes.put("/robots.txt", new Response(403, "Just a moment...", Map.of()));
        routes.put("/timetable.csv", new Response(200, "a,b\n", Map.of()));

        assertThat(source().fetch(CacheValidators.none())).isInstanceOf(FetchResult.Blocked.class);
        assertThat(requestedPaths).containsExactly("/robots.txt");
    }

    @Test
    void serverErrorOnRobotsTxtIsTreatedAsDisallowAll() throws Exception {
        routes.put("/robots.txt", new Response(500, "", Map.of()));
        assertThat(source().fetch(CacheValidators.none())).isInstanceOf(FetchResult.Blocked.class);
    }

    @Test
    void conditionalGetReturnsNotModifiedWhenEtagMatches() throws Exception {
        routes.put("/timetable.csv", new Response(200, "a,b\n", Map.of("ETag", "\"v1\"")));

        FetchResult result = source().fetch(new CacheValidators("\"v1\"", null));

        assertThat(result).isInstanceOf(FetchResult.NotModified.class);
        assertThat(ifNoneMatch).containsExactly("\"v1\"");
    }

    @Test
    void robotsTxtIsCachedBetweenFetches() throws Exception {
        routes.put("/timetable.csv", new Response(200, "a,b\n", Map.of()));
        HttpTimetableSource source = source();

        source.fetch(CacheValidators.none());
        source.fetch(CacheValidators.none());

        assertThat(requestedPaths).containsExactly("/robots.txt", "/timetable.csv", "/timetable.csv");
    }

    @Test
    void backsOffOnTooManyRequests() throws Exception {
        routes.put("/timetable.csv", new Response(429, "", Map.of("Retry-After", "3600")));
        FetchResult result = source().fetch(CacheValidators.none());
        assertThat(result).isInstanceOf(FetchResult.Blocked.class);
        assertThat(((FetchResult.Blocked) result).reason()).contains("back off");
    }

    @Test
    void doesNotFollowRedirects() {
        routes.put("/timetable.csv", new Response(302, "", Map.of("Location", "http://elsewhere.invalid/x")));
        assertThatThrownBy(() -> source().fetch(CacheValidators.none()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("unexpected HTTP 302");
    }

    @Test
    void rejectsNonHttpUrls() {
        assertThatThrownBy(() -> new HttpTimetableSource(URI.create("file:///etc/passwd"), "YURoom student project",
                HttpTimetableSource.defaultClient(Duration.ofSeconds(1)), HostRateLimiter.realTime(Duration.ZERO),
                Duration.ofSeconds(1), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
