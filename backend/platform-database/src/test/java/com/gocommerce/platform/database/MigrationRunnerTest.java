package com.gocommerce.platform.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationRunnerTest {

    private final DefaultApplicationArguments noArguments = new DefaultApplicationArguments();
    private final java.util.concurrent.atomic.AtomicInteger shutdowns = new java.util.concurrent.atomic.AtomicInteger();
    private final Runnable recordShutdown = shutdowns::incrementAndGet;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOutsideSharedPackage.class);

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ServiceOutsideSharedPackage {
    }

    @Test
    void migrateProfileDiscoversTheRunnerThroughAutoConfigurationMetadata() {
        // Real services scan com.gocommerce.<service>, not this shared package. This test
        // catches a runner that is annotated but missing from AutoConfiguration.imports.
        contextRunner.withPropertyValues("spring.profiles.active=migrate")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context)
                        .hasSingleBean(MigrationRunner.class));
    }

    @Test
    void normalServiceStartupDoesNotCreateTheMigrationRunner() {
        contextRunner.run(context -> org.assertj.core.api.Assertions.assertThat(context)
                .doesNotHaveBean(MigrationRunner.class));
    }

    private Flyway flywayWith(MigrationInfo current, MigrationInfo... pending) {
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(info.current()).thenReturn(current);
        when(info.pending()).thenReturn(pending);
        Flyway flyway = mock(Flyway.class);
        when(flyway.info()).thenReturn(info);
        return flyway;
    }

    private MigrationInfo version(String number, String description) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(number));
        when(info.getDescription()).thenReturn(description);
        return info;
    }

    @Test
    void theJobShutsItselfDownSoTheContainerCanComplete() {
        // Services that schedule work hold non-daemon threads; without this the job would
        // migrate successfully and then hang forever.
        MigrationRunner runner = new MigrationRunner(
                Optional.of(flywayWith(version("5", "payment refunds"))), recordShutdown);

        runner.run(noArguments);

        org.assertj.core.api.Assertions.assertThat(shutdowns.get()).isEqualTo(1);
    }

    @Test
    void aFailedRunDoesNotShutDownCleanlyAndSoFailsTheJob() {
        MigrationRunner runner = new MigrationRunner(Optional.empty(), recordShutdown);

        assertThatThrownBy(() -> runner.run(noArguments)).isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThat(shutdowns.get()).isZero();
    }

    @Test
    void aFullyMigratedDatabaseCompletesTheJob() {
        MigrationRunner runner = new MigrationRunner(Optional.of(flywayWith(version("5", "payment refunds"))), recordShutdown);

        assertThatCode(() -> runner.run(noArguments)).doesNotThrowAnyException();
    }

    @Test
    void anEmptyDatabaseIsReportedRatherThanTreatedAsAFailure() {
        // A brand new database has no current version until the first migration lands.
        MigrationRunner runner = new MigrationRunner(Optional.of(flywayWith(null)), recordShutdown);

        assertThatCode(() -> runner.run(noArguments)).doesNotThrowAnyException();
    }

    @Test
    void migrationsLeftPendingFailTheJobSoTheDeployStops() {
        // If the job exits successfully while work remains, the next pods start against a
        // schema their entities do not match.
        MigrationRunner runner = new MigrationRunner(
                Optional.of(flywayWith(version("4", "checkout recovery"), version("5", "payment refunds"))),
                recordShutdown);

        assertThatThrownBy(() -> runner.run(noArguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still pending");
    }

    @Test
    void anUnconfiguredFlywayFailsLoudlyInsteadOfDoingNothing() {
        MigrationRunner runner = new MigrationRunner(Optional.empty(), recordShutdown);

        assertThatThrownBy(() -> runner.run(noArguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nothing would be applied");
    }
}
