package com.gocommerce.search.config;

import com.gocommerce.search.model.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@Configuration
public class ElasticsearchIndexInitializer {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    @Bean
    ApplicationRunner initIndex(ElasticsearchOperations ops) {
        return args -> {
            try {
                var indexOps = ops.indexOps(ProductDocument.class);
                if (indexOps == null) {
                    log.warn("Skipping Elasticsearch index initialization because index operations are unavailable");
                    return;
                }
                if (!indexOps.exists()) {
                    indexOps.create(ProductIndexSettings.settings());
                }
                indexOps.putMapping(indexOps.createMapping(ProductDocument.class));
            } catch (Exception ex) {
                log.warn("Failed to initialize Elasticsearch products index; continuing startup", ex);
            }
        };
    }
}
