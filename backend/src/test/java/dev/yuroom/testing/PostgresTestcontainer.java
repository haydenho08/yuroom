package dev.yuroom.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL 16 for integration tests. As a bean, Spring starts it before the DataSource
 * and wires the JDBC URL in via @ServiceConnection; tests sharing a context share the container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
