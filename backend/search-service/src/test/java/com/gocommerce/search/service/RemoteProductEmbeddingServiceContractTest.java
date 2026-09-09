package com.gocommerce.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.search.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RemoteProductEmbeddingServiceContractTest {

    @Test
    void sendsAndParsesTheEmbeddingContract() throws Exception {
        var restTemplate = new RestTemplate();
        var server = MockRestServiceServer.bindTo(restTemplate).build();
        var properties = new EmbeddingProperties();
        properties.setBaseUrl("http://embedding");
        properties.setDimensions(3);
        var client = new RemoteProductEmbeddingService(restTemplate, properties);
        var root = Path.of("..", "..", "contracts", "search-embedding");
        String request = Files.readString(root.resolve("embedding-request.json"));
        String response = Files.readString(root.resolve("embedding-response.json"));
        server.expect(requestTo("http://embedding/api/v1/embeddings"))
                .andExpect(content().json(request, true))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        var embeddings = client.embedAll(List.of("synthetic red shoe", "synthetic blue bag"));

        assertThat(embeddings).containsExactly(
                List.of(1.0f, 0.0f, 0.0f),
                List.of(0.0f, 1.0f, 0.0f));
        server.verify();
    }
}
