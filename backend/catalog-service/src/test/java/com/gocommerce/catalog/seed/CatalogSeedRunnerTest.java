package com.gocommerce.catalog.seed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSeedRunnerTest {
    @Test
    void finiteSeedJobWritesConfiguredCountAndShutsDown() throws Exception {
        var seeder = mock(CatalogSeeder.class);
        when(seeder.seed(25)).thenReturn(25);
        var shutdowns = new AtomicInteger();

        new CatalogSeedRunner(seeder, 25, shutdowns::incrementAndGet)
                .run(new DefaultApplicationArguments());

        verify(seeder).seed(25);
        assertThat(shutdowns).hasValue(1);
    }

    @Test
    void failedSeedDoesNotReportACompletedJob() {
        var seeder = mock(CatalogSeeder.class);
        when(seeder.seed(25)).thenThrow(new IllegalStateException("database unavailable"));
        var shutdowns = new AtomicInteger();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new CatalogSeedRunner(seeder, 25, shutdowns::incrementAndGet)
                        .run(new DefaultApplicationArguments()))
                .hasMessageContaining("database unavailable");
        assertThat(shutdowns).hasValue(0);
    }
}
