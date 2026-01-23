package com.gocommerce.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommendation")
public class RecommendationProperties {

    /**
     * Base URL of recommendation-service.
     * e.g. http://localhost:8085 (adjust to your actual port)
     */
    private String baseUrl = "http://localhost:8085";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
