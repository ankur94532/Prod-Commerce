package com.gocommerce.search.config;

import com.gocommerce.search.service.SearchIndexManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    /**
     * Ensures {@code products} exists as an alias over a concrete index, rather than as a
     * concrete index itself. This used to create the index directly, which is what made
     * every rebuild a destructive delete-and-recreate of the thing readers were using.
     */
    @Bean
    ApplicationRunner initIndex(SearchIndexManager indexManager) {
        return args -> {
            try {
                String current = indexManager.currentIndex();
                if (current != null) {
                    log.info("Search alias {} already points at {}", SearchIndexManager.ALIAS, current);
                    return;
                }
                if (indexManager.hasLegacyConcreteIndex()) {
                    // Migrating means dropping that index, which loses its documents unless
                    // they are rebuilt first. The next reindex handles it deliberately.
                    log.warn("{} exists as a concrete index, not an alias. Run a reindex to migrate it; "
                            + "until then rebuilds cannot be atomic.", SearchIndexManager.ALIAS);
                    return;
                }
                String created = indexManager.createIndex();
                indexManager.promote(created);
                log.info("Initialised search alias {} over a new index {}", SearchIndexManager.ALIAS, created);
            } catch (Exception ex) {
                log.warn("Failed to initialise the Elasticsearch products index; continuing startup", ex);
            }
        };
    }
}
