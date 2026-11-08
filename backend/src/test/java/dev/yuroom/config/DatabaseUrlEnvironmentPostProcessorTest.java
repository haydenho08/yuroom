package dev.yuroom.config;

import static dev.yuroom.config.DatabaseUrlEnvironmentPostProcessor.fromLibpqUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.yuroom.config.DatabaseUrlEnvironmentPostProcessor.JdbcSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void translatesHostStyleUrls() {
        assertThat(fromLibpqUrl("postgres://u7:s3cret@db.example.com:6543/d9")).contains(
                new JdbcSettings("jdbc:postgresql://db.example.com:6543/d9?reWriteBatchedInserts=true", "u7", "s3cret"));
    }

    @Test
    void keepsQueryParametersAndDefaultsThePort() {
        assertThat(fromLibpqUrl("postgresql://u:p@host/db?sslmode=require").orElseThrow().url())
                .isEqualTo("jdbc:postgresql://host:5432/db?reWriteBatchedInserts=true&sslmode=require");
    }

    @Test
    void decodesEscapedCredentials() {
        JdbcSettings s = fromLibpqUrl("postgres://me%40x:p%3Aw%2Fd@h:5432/db").orElseThrow();
        assertThat(s.username()).isEqualTo("me@x");
        assertThat(s.password()).isEqualTo("p:w/d");
    }

    @Test
    void leavesJdbcUrlsAndMissingValuesAlone() {
        assertThat(fromLibpqUrl("jdbc:postgresql://localhost:5433/yuroom")).isEmpty();
        assertThat(fromLibpqUrl(null)).isEmpty();
        assertThat(fromLibpqUrl("")).isEmpty();
    }

    @Test
    void rejectsUrlsWithoutDatabaseOrCredentials() {
        assertThatThrownBy(() -> fromLibpqUrl("postgres://host:5432/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fromLibpqUrl("postgres://u:p@host:5432")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overridesTheDatasourceProperties() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new MapPropertySource("test", Map.of(
                "DATABASE_URL", "postgres://u:p@h:1/db",
                "spring.datasource.url", "jdbc:postgresql://localhost:5433/yuroom")));
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h:1/db?reWriteBatchedInserts=true");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("u");
    }
}
