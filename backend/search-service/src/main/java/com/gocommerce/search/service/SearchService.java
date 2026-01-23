package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.dto.SearchDtos.SearchResultItem;
import com.gocommerce.search.metrics.SearchMetrics;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

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
    private final SearchMetrics searchMetrics;

    @Autowired
    public SearchService(ProductSearchRepository productSearchRepository,
                         SearchCache searchCache,
                         CatalogClient catalogClient,
                         ElasticsearchOperations elasticsearchOperations,
                         SearchMetrics searchMetrics) {
        this.productSearchRepository = productSearchRepository;
        this.searchCache = searchCache;
        this.catalogClient = catalogClient;
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchMetrics = searchMetrics;
    }

    // overload for tests that were using 4-arg ctor
    public SearchService(ProductSearchRepository productSearchRepository,
                         SearchCache searchCache,
                         CatalogClient catalogClient,
                         ElasticsearchOperations elasticsearchOperations) {
        this(productSearchRepository, searchCache, catalogClient, elasticsearchOperations, null);
    }

    @CircuitBreaker(name = "searchCore", fallbackMethod = "searchFallback")
    public SearchResponse search(SearchRequest request) {
        String query = request.query();
        String categoryFilter = request.category();
        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 20;

        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0);
        }

        // normalize for cache key
        SearchRequest normalizedRequest = new SearchRequest(query, categoryFilter, page, size);

        var cached = searchCache.get(normalizedRequest);
        if (cached.isPresent()) {
            if (searchMetrics != null) {
                searchMetrics.recordCachedSearch();
            }
            return cached.get();
        }

        if (searchMetrics != null) {
            searchMetrics.incrementCacheMiss();
            return searchMetrics.timeSearch(() -> doSearchAndCache(normalizedRequest));
        } else {
            return doSearchAndCache(normalizedRequest);
        }
    }

    // Fallback for searchCore CB
    @SuppressWarnings("unused")
    public SearchResponse searchFallback(SearchRequest request, Throwable ex) {
        String q = (request != null ? request.query() : null);
        String cat = (request != null ? request.category() : null);
        log.warn("SearchService.search fallback triggered for query='{}', category='{}'",
                q, cat, ex);

        if (searchMetrics != null) {
            searchMetrics.incrementZeroResult();
        }

        return new SearchResponse(List.of(), 0);
    }

    private SearchResponse doSearchAndCache(SearchRequest normalizedRequest) {
        String query = normalizedRequest.query();
        String categoryFilter = normalizedRequest.category();
        int page = normalizedRequest.page() != null ? normalizedRequest.page() : 0;
        int size = normalizedRequest.size() != null ? normalizedRequest.size() : 20;

        var pageable = PageRequest.of(page, size);

        var pageResult = (categoryFilter != null && !categoryFilter.isBlank())
                ? productSearchRepository.searchByNameAndCategory(query, categoryFilter, pageable)
                : productSearchRepository.searchByNameOrCategory(query, query, pageable);

        List<SearchResultItem> items = pageResult.getContent().stream()
                .map(this::toResultItem)
                .toList();

        if (searchMetrics != null && items.isEmpty()) {
            searchMetrics.incrementZeroResult();
        }

        SearchResponse response = new SearchResponse(items, pageResult.getTotalElements());
        searchCache.put(normalizedRequest, response);

        return response;
    }

    /**
     * Recreates the index and reindexes all products from catalog-service.
     *
     * IMPORTANT: This method is now "boringly robust":
     *  - Any exception (catalog down, ES down, etc.) is caught.
     *  - We log a warning and return 0 instead of propagating 500 to the caller.
     */
    @Transactional
    public int reindexProducts() {
        try {
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

            if (searchMetrics != null) {
                searchMetrics.onReindexCompleted(docs.size());
            }

            return docs.size();
        } catch (Exception ex) {
            log.warn("Reindex failed, returning indexed=0 (catalog or ES might be down)", ex);
            if (searchMetrics != null) {
                searchMetrics.onReindexCompleted(0);
            }
            return 0;
        }
    }

    // ---------- single-product index helpers ----------

    public void indexProductFromPayload(Map<String, Object> productPayload) {
        ProductDocument doc = toDocument(productPayload);
        if (doc.getId() == null || doc.getId().isBlank()) {
            throw new IllegalArgumentException("Product id is required for indexing");
        }

        productSearchRepository.save(doc);
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();

        log.info("Indexed single product {} into Elasticsearch", doc.getId());
    }

    public void deleteProductFromIndex(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        productSearchRepository.deleteById(id);
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();

        log.info("Deleted product {} from Elasticsearch index", id);
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
        if (id == null) {
            id = asString(p.get("_id"));
        }

        String name = firstNonBlank(
                asString(p.get("name")),
                asString(p.get("title"))
        );

        String slug = firstNonBlank(
                asString(p.get("slug")),
                slugify(name)
        );

        String category = firstNonBlank(
                asString(p.get("category")),
                asString(p.get("categorySlug")),
                asString(p.get("categoryName"))
        );

        BigDecimal price = asBigDecimal(p.get("price"));
        String currency = firstNonBlank(asString(p.get("currency")), "INR");

        var imageUrls = asStringList(p.get("imageUrls"));

        String thumbnailUrl = firstNonBlank(
                asString(p.get("thumbnailUrl")),
                asString(p.get("imageUrl")),
                asString(p.get("image")),
                !imageUrls.isEmpty() ? imageUrls.get(0) : null
        );

        List<String> tags = asStringList(p.get("tags"));

        return new ProductDocument(
                id,
                slug,
                name,
                category,
                price,
                currency,
                tags,
                thumbnailUrl
        );
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
