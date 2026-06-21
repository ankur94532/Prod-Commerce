package com.gocommerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRouteConfigTest {

    @Test
    void gatewayRoutesSearchReindexPostToSearchService() throws Exception {
        String config = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(config).contains("id: search-service");
        assertThat(config).contains("id: search-service-reindex");
        assertThat(config).contains("SEARCH_SERVICE_URI:http://localhost:8084");
        assertThat(config).contains("Path=/api/v1/search/reindex");
        assertThat(config).contains("Method=POST");
        assertThat(config).contains("Path=/api/v1/search/**");
        assertThat(config).contains("POST");
    }
}
