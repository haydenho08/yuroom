package dev.yuroom.buildings;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
class BuildingDirectoryConfig {

    private static final Logger log = LoggerFactory.getLogger(BuildingDirectoryConfig.class);

    @Bean
    BuildingDirectory buildingDirectory(
            @Value("${yuroom.buildings:classpath:buildings/buildings.csv}") Resource file) throws IOException {
        try (Reader in = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            BuildingDirectory directory = BuildingDirectory.read(in);
            log.info("Loaded {} curated buildings from {}", directory.size(), file);
            return directory;
        }
    }
}
