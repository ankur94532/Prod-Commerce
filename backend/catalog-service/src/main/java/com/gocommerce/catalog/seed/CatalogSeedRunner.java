package com.gocommerce.catalog.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("seed")
public class CatalogSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CatalogSeedRunner.class);
    private final CatalogSeeder seeder;
    private final int size;
    private final Runnable shutdown;

    public CatalogSeedRunner(CatalogSeeder seeder, @Value("${catalog.seed.size:1000}") int size,
                             ConfigurableApplicationContext context) {
        this(seeder, size, () -> System.exit(SpringApplication.exit(context, () -> 0)));
    }

    CatalogSeedRunner(CatalogSeeder seeder, int size, Runnable shutdown) {
        this.seeder = seeder;
        this.size = size;
        this.shutdown = shutdown;
    }

    @Override
    public void run(ApplicationArguments args) {
        int saved = seeder.seed(size);
        log.info("Seeded or updated {} synthetic catalog products", saved);
        shutdown.run();
    }
}
