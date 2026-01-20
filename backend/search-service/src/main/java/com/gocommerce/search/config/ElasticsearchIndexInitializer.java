package com.gocommerce.search.config;

import com.gocommerce.search.model.ProductDocument;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@Configuration
public class ElasticsearchIndexInitializer {

    @Bean
    ApplicationRunner initIndex(ElasticsearchOperations ops) {
        return args -> {
            var indexOps = ops.indexOps(ProductDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping(indexOps.createMapping(ProductDocument.class));
            }
        };
    }
}
