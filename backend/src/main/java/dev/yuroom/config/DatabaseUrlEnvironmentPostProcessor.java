package dev.yuroom.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Hosts like Heroku, Render, Railway and Koyeb hand the app a {@code DATABASE_URL} in libpq form,
 * {@code postgres://user:pass@host:5432/db}, but JDBC wants {@code jdbc:postgresql://host:5432/db} with
 * the credentials passed separately. This translates the first into the second before Spring builds
 * the DataSource, so the same image runs on any of them. A {@code jdbc:} URL is left alone.
 *
 * <p>Registered in {@code META-INF/spring.factories}; runs before any bean exists.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** What JDBC needs, split out of one libpq URL. */
    public record JdbcSettings(String url, String username, String password) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        fromLibpqUrl(env.getProperty("DATABASE_URL")).ifPresent(s -> env.getPropertySources().addFirst(
                new MapPropertySource("databaseUrl", Map.of(
                        "spring.datasource.url", s.url(),
                        "spring.datasource.username", s.username(),
                        "spring.datasource.password", s.password()))));
    }

    /** Pure, so it's unit-tested without Spring. Empty for null, blank or jdbc: URLs. */
    public static Optional<JdbcSettings> fromLibpqUrl(String value) {
        if (value == null || !(value.startsWith("postgres://") || value.startsWith("postgresql://"))) {
            return Optional.empty();
        }
        URI uri = URI.create(value);
        String userInfo = uri.getRawUserInfo();
        if (uri.getHost() == null || userInfo == null || uri.getRawPath() == null || uri.getRawPath().length() < 2) {
            throw new IllegalArgumentException("DATABASE_URL must look like postgres://user:password@host:port/database");
        }
        int colon = userInfo.indexOf(':');
        String user = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
        String password = colon < 0 ? "" : decode(userInfo.substring(colon + 1));
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost()).append(':').append(port)
                .append(uri.getRawPath()).append("?reWriteBatchedInserts=true");
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            jdbc.append('&').append(uri.getRawQuery());
        }
        return Optional.of(new JdbcSettings(jdbc.toString(), user, password));
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
