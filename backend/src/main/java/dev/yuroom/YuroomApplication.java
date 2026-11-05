package dev.yuroom;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class YuroomApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuroomApplication.class, args);
    }

    /** Injected everywhere "now" matters, so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
