package com.gocommerce.search.service;

import com.gocommerce.search.config.EmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class RemoteProductEmbeddingService implements ProductEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RemoteProductEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final int dimensions;

    public RemoteProductEmbeddingService(RestTemplate restTemplate, EmbeddingProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = trimTrailingSlash(properties.getBaseUrl());
        this.dimensions = properties.getDimensions();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<Float> embed(String text) {
        return embedAll(List.of(text != null ? text : "")).get(0);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            EmbeddingResponse response = restTemplate.postForObject(
                    baseUrl + "/api/v1/embeddings",
                    new EmbeddingRequest(texts),
                    EmbeddingResponse.class);

            if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
                throw new IllegalStateException("Embedding service returned an invalid response");
            }

            response.embeddings().forEach(this::validateDimensions);
            return response.embeddings();
        } catch (RestClientException ex) {
            log.warn("Embedding service request failed at {}", baseUrl, ex);
            throw ex;
        }
    }

    private void validateDimensions(List<Float> embedding) {
        if (embedding == null || embedding.size() != dimensions) {
            throw new IllegalStateException("Expected embedding dimension " + dimensions
                    + " but got " + (embedding != null ? embedding.size() : 0));
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record EmbeddingRequest(List<String> texts) {}

    public record EmbeddingResponse(
            List<List<Float>> embeddings,
            int dimensions,
            String model,
            Map<String, Object> usage
    ) {}
}
