package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.dto.SearchDtos.SearchResultItem;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ProductSearchRepository productSearchRepository;
    private final SearchCache searchCache;

    private final CatalogClient catalogClient;
    private final ElasticsearchOperations elasticsearchOperations;

    public SearchService(ProductSearchRepository productSearchRepository,
            SearchCache searchCache,
            CatalogClient catalogClient,
            ElasticsearchOperations elasticsearchOperations) {
        this.productSearchRepository = productSearchRepository;
        this.searchCache = searchCache;
        this.catalogClient = catalogClient;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public SearchResponse search(SearchRequest request) {
        String query = request.query();
        String categoryFilter = request.category();
        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 20;

        if (query == null || query.isBlank()) {
            // don't cache empty query for now
            return new SearchResponse(List.of(), 0);
        }

        // 1) Cache lookup
        var normalizedRequest = new SearchRequest(query, categoryFilter, page, size);
        var cached = searchCache.get(normalizedRequest);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) Elasticsearch query
        var pageable = PageRequest.of(page, size);

        var pageResult = (categoryFilter != null && !categoryFilter.isBlank())
                ? productSearchRepository.searchByNameAndCategory(query, categoryFilter, pageable)
                : productSearchRepository.searchByNameOrCategory(query, query, pageable);

        List<SearchResultItem> items = pageResult.getContent().stream()
                .map(this::toResultItem)
                .toList();

        SearchResponse response = new SearchResponse(items, pageResult.getTotalElements());

        // 3) Store in cache
        searchCache.put(normalizedRequest, response);

        return response;
    }

    /**
     * Recreates the index and reindexes all products from catalog-service.
     */
    @Transactional
    public int reindexProducts() {
        var indexOps = elasticsearchOperations.indexOps(ProductDocument.class);

        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping(indexOps.createMapping(ProductDocument.class));

        List<Map<String, Object>> products = catalogClient.fetchAllProducts();
        List<ProductDocument> docs = products.stream()
                .map(this::toDocument)
                .filter(d -> d.getId() != null && !d.getId().isBlank())
                .toList();

        productSearchRepository.saveAll(docs);
        indexOps.refresh();

        log.info("Reindexed {} products into Elasticsearch", docs.size());
        return docs.size();
    }

    private SearchResultItem toResultItem(ProductDocument doc) {
        return new SearchResultItem(
                doc.getId(),
                doc.getSlug(),
                doc.getName(),
                doc.getCategory(),
                doc.getPrice(),
                doc.getCurrency(),
                doc.getThumbnailUrl());
    }

    // ---------------- helpers ----------------

    private ProductDocument toDocument(Map<String, Object> p) {
        String id = asString(p.get("id"));
        if (id == null)
            id = asString(p.get("_id"));

        String name = firstNonBlank(
                asString(p.get("name")),
                asString(p.get("title")));

        String slug = firstNonBlank(
                asString(p.get("slug")),
                slugify(name));

        String category = firstNonBlank(
                asString(p.get("category")),
                asString(p.get("categoryName")));

        BigDecimal price = asBigDecimal(p.get("price"));
        String currency = firstNonBlank(asString(p.get("currency")), "INR");

        String thumbnailUrl = firstNonBlank(
                asString(p.get("thumbnailUrl")),
                asString(p.get("imageUrl")),
                asString(p.get("image")));

        List<String> tags = asStringList(p.get("tags"));

        return new ProductDocument(
                id,
                slug,
                name,
                category,
                price,
                currency,
                tags,
                thumbnailUrl);
    }

    private static String asString(Object o) {
        if (o == null)
            return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o == null)
            return null;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> asStringList(Object o) {
        if (o == null)
            return List.of();
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        // if it's a comma-separated string
        String s = String.valueOf(o);
        if (s.isBlank())
            return List.of();
        return List.of(s.split(",")).stream().map(String::trim).filter(x -> !x.isBlank()).toList();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank())
                return v;
        }
        return null;
    }

    private static String slugify(String s) {
        if (s == null)
            return null;
        String slug = s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? null : slug;
    }
}
