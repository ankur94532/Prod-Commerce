package com.gocommerce.search.cache;

import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;

import java.util.Optional;

public interface SearchCache {

    Optional<SearchResponse> get(SearchRequest request);

    void put(SearchRequest request, SearchResponse response);
}
