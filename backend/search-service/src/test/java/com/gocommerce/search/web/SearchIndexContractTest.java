package com.gocommerce.search.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchIndexContractTest {

    @Test
    void acceptsTheIndexProductContractProducedByCatalogService() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "contracts", "catalog-search", "index-product.json"));
        var service = mock(SearchService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SearchController(service)).build();

        mvc.perform(post("/api/v1/search/index-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        var payload = ArgumentCaptor.forClass(Map.class);
        verify(service).indexProductFromPayload(payload.capture());
        var mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode actual = mapper.valueToTree(payload.getValue());
        assertThat(actual).isEqualTo(mapper.readTree(fixture));
    }
}
