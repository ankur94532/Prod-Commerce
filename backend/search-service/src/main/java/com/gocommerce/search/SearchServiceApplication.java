package com.gocommerce.search;

import com.gocommerce.search.config.CatalogProperties;
import com.gocommerce.search.config.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties({CatalogProperties.class, RecommendationProperties.class})
@EnableScheduling
@SpringBootApplication
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
