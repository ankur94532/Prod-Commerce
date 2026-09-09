package com.gocommerce.platform.database;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/** Makes the migration job lifecycle available outside each service's component-scan root. */
@AutoConfiguration
@Profile("migrate")
public class MigrationAutoConfiguration {

    @Bean
    MigrationRunner migrationRunner(Optional<Flyway> flyway, ConfigurableApplicationContext context) {
        return new MigrationRunner(flyway, context);
    }
}
