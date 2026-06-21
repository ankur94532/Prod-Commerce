package com.gocommerce.search.config;

import com.gocommerce.search.model.ProductDocument;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    private String baseUrl = "http://localhost:8090";
    private int dimensions = ProductDocument.SEARCH_EMBEDDING_DIMENSIONS;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }
}
