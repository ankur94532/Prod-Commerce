package com.gocommerce.search;

import com.gocommerce.search.config.CatalogProperties;
import com.gocommerce.search.config.EmbeddingProperties;
import com.gocommerce.search.config.RecommendationProperties;
import com.gocommerce.search.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties({
        CatalogProperties.class,
        RecommendationProperties.class,
        EmbeddingProperties.class,
        SearchProperties.class
})
@EnableScheduling
@SpringBootApplication
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
