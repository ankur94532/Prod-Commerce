package com.gocommerce.platform.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

/**
 * Runs schema migrations as a deliberate step, then exits.
 *
 * <p>Migrations used to run inside every service's startup. On a rolling deploy that means
 * N replicas racing for the migration lock, a long migration failing readiness across the
 * fleet, and nothing at all stopping a change that the currently-running pods cannot read.
 * Deployed environments now start with Flyway disabled and run this first, as a job, so the
 * schema is already correct before any new pod starts.
 *
 * <p>The guard against forgetting is {@code spring.jpa.hibernate.ddl-auto=validate}: a
 * service whose schema is behind its entities fails to start rather than serving errors.
 */
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final Optional<Flyway> flyway;
    private final Runnable shutdown;

    public MigrationRunner(Optional<Flyway> flyway, ConfigurableApplicationContext context) {
        // A job has to end. Services that schedule work hold non-daemon threads, so the
        // JVM would otherwise sit idle after the migration finished and the job would
        // never complete.
        this(flyway, () -> System.exit(SpringApplication.exit(context, () -> 0)));
    }

    MigrationRunner(Optional<Flyway> flyway, Runnable shutdown) {
        this.flyway = flyway;
        this.shutdown = shutdown;
    }

    @Override
    public void run(ApplicationArguments args) {
        Flyway migrations = flyway.orElseThrow(() -> new IllegalStateException(
                "The migrate profile is active but Flyway is not configured; nothing would be applied"));

        // Flyway has already migrated during context startup. Report what the database is
        // actually at, so the job's logs answer "what did this deploy change?".
        MigrationInfo current = migrations.info().current();
        if (current == null) {
            log.warn("No migrations are recorded for this database");
        } else {
            log.info("Schema is at version {} ({})", current.getVersion(), current.getDescription());
        }
        MigrationInfo[] pending = migrations.info().pending();
        if (pending.length > 0) {
            // Should be unreachable: startup migrates. Failing is better than a silent gap.
            throw new IllegalStateException(pending.length + " migrations are still pending after the run");
        }
        shutdown.run();
    }
}
