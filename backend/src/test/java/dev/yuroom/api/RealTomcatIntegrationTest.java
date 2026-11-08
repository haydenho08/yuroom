package dev.yuroom.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.yuroom.ingestion.IngestionJob;
import dev.yuroom.ingestion.RunStatus;
import dev.yuroom.testing.PostgresTestcontainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Behaviour that needs a real Tomcat. Mainly the production proxy setting (MockMvc skips Tomcat's valves, so this can't
 * be a MockMvc test). Requests come from 127.0.0.1, which RemoteIpValve trusts like the host's proxy.
 * X-Forwarded-For is read from the right: the proxy appends the real client address last, so that's
 * the one we must use, and anything a client put further left is ignored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.forward-headers-strategy=native",
        "yuroom.ingestion.enabled=false",
        "yuroom.ingestion.source=FILE",
        "yuroom.ingestion.file.path=src/test/resources/fixtures/api-timetable.csv"
})
@Import(PostgresTestcontainer.class)
class RealTomcatIntegrationTest {

    @LocalServerPort
    int port;
    @Autowired
    IngestionJob ingestion;
    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private long roomId;

    @BeforeEach
    void setUp() {
        if (jdbc.queryForObject("select count(*) from room", Long.class) == 0) {
            assertThat(ingestion.run(true).status()).isEqualTo(RunStatus.SUCCESS);
        }
        jdbc.update("delete from occupancy_report");
        roomId = jdbc.queryForObject("select min(id) from room", Long.class);
    }

    private HttpResponse<String> report(String xForwardedFor, String proto) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/rooms/" + roomId + "/reports"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("X-Forwarded-For", xForwardedFor);
        if (proto != null) {
            b.header("X-Forwarded-Proto", proto);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rateLimitsByTheAddressTheProxySaw() throws Exception {
        assertThat(report("192.0.2.10", null).statusCode()).isEqualTo(201);
        // A different student behind the same proxy is a different client.
        assertThat(report("192.0.2.11", null).statusCode()).isEqualTo(201);
        // The first student again: limited.
        assertThat(report("192.0.2.10", null).statusCode()).isEqualTo(429);
    }

    @Test
    void aFakeHeaderFromTheClientDoesNotChangeTheKey() throws Exception {
        assertThat(report("192.0.2.20", null).statusCode()).isEqualTo(201);
        // The client sends "X-Forwarded-For: 6.6.6.6"; the proxy appends the real address.
        assertThat(report("6.6.6.6, 192.0.2.20", null).statusCode()).isEqualTo(429);
        assertThat(report("7.7.7.7, 192.0.2.20", null).statusCode()).isEqualTo(429);
    }

    @Test
    void rootServesTheAppShell() throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("spa-shell");
        assertThat(res.headers().firstValue("Content-Security-Policy")).isPresent();
    }

    @Test
    void httpsAtTheProxyTurnsOnHsts() throws Exception {
        assertThat(report("192.0.2.30", "https").headers().firstValue("Strict-Transport-Security"))
                .contains("max-age=31536000");
        assertThat(report("192.0.2.31", null).headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
