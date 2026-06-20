package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.dto.SearchDtos.SearchResultItem;
import com.gocommerce.search.metrics.SearchMetrics;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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
        int page = request.page() != null ? Math.max(request.page(), 0) : 0;
        int size = request.size() != null ? Math.max(1, Math.min(request.size(), 100)) : 20;

        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0);
        }

        // normalize for cache key
        SearchRequest normalizedRequest = new SearchRequest(
                query.trim(),
                normalized(categoryFilter),
                normalized(request.brand()),
                request.minPrice(),
                request.maxPrice(),
                request.inStock(),
                normalized(request.color()),
                normalized(request.type()),
                normalized(request.fit()),
                normalized(request.storage()),
                normalized(request.memory()),
                normalized(request.material()),
                normalized(request.sort()),
                page,
                size);

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
        int page = normalizedRequest.page() != null ? normalizedRequest.page() : 0;
        int size = normalizedRequest.size() != null ? normalizedRequest.size() : 20;

        var queryBuilder = NativeQuery.builder()
                .withQuery(buildSearchQuery(normalizedRequest))
                .withPageable(PageRequest.of(page, size))
                .withTrackTotalHits(true);

        List<SortOptions> sortOptions = sortOptions(normalizedRequest.sort());
        if (!sortOptions.isEmpty()) {
            queryBuilder.withSort(sortOptions);
        }

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(
                queryBuilder.build(), ProductDocument.class);

        List<SearchResultItem> items = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toResultItem)
                .toList();

        if (searchMetrics != null && items.isEmpty()) {
            searchMetrics.incrementZeroResult();
        }

        long total = searchHits.getTotalHits();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        SearchResponse response = new SearchResponse(items, total, page, size, totalPages);
        searchCache.put(normalizedRequest, response);

        return response;
    }

    private Query buildSearchQuery(SearchRequest request) {
        List<Query> filters = new ArrayList<>();
        addTermFilter(filters, "category", request.category());
        addTermFilter(filters, "brand", request.brand());
        addTermFilter(filters, "color", request.color());
        addTermFilter(filters, "type", request.type());
        addTermFilter(filters, "fit", request.fit());
        addTermFilter(filters, "storage", request.storage());
        addTermFilter(filters, "memory", request.memory());
        addTermFilter(filters, "material", request.material());
        addPriceFilter(filters, request.minPrice(), request.maxPrice());
        addStockFilter(filters, request.inStock());

        return Query.of(q -> q.bool(b -> {
            b.must(buildTextQuery(request.query()));
            if (!filters.isEmpty()) {
                b.filter(filters);
            }
            return b;
        }));
    }

    private Query buildTextQuery(String query) {
        return Query.of(q -> q.bool(b -> b
                .should(Query.of(s -> s.matchPhrase(mp -> mp
                        .field("name")
                        .query(query)
                        .boost(6.0f))))
                .should(Query.of(s -> s.match(m -> m
                        .field("name")
                        .query(query)
                        .operator(Operator.And)
                        .boost(4.0f))))
                .should(Query.of(s -> s.match(m -> m
                        .field("description")
                        .query(query)
                        .operator(Operator.And)
                        .boost(2.0f))))
                .should(Query.of(s -> s.match(m -> m
                        .field("searchText")
                        .query(query)
                        .operator(Operator.And)
                        .boost(2.0f))))
                .should(Query.of(s -> s.match(m -> m
                        .field("searchText")
                        .query(query)
                        .operator(Operator.Or)
                        .boost(1.0f))))
                .minimumShouldMatch("1")));
    }

    private void addTermFilter(List<Query> filters, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filters.add(Query.of(q -> q.term(t -> t
                .field(field)
                .value(value)
                .caseInsensitive(true))));
    }

    private void addPriceFilter(List<Query> filters, BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return;
        }
        filters.add(Query.of(q -> q.range(r -> {
            r.field("price");
            if (minPrice != null) {
                r.gte(JsonData.of(minPrice));
            }
            if (maxPrice != null) {
                r.lte(JsonData.of(maxPrice));
            }
            return r;
        })));
    }

    private void addStockFilter(List<Query> filters, Boolean inStock) {
        if (!Boolean.TRUE.equals(inStock)) {
            return;
        }
        filters.add(Query.of(q -> q.range(r -> r
                .field("stockQuantity")
                .gt(JsonData.of(0)))));
    }

    private List<SortOptions> sortOptions(String sort) {
        if (sort == null || sort.isBlank() || "relevance".equals(sort)) {
            return List.of();
        }
        return switch (sort) {
            case "newest" -> List.of(fieldSort("productId", SortOrder.Desc));
            case "price_asc" -> List.of(fieldSort("price", SortOrder.Asc));
            case "price_desc" -> List.of(fieldSort("price", SortOrder.Desc));
            case "name_asc" -> List.of(fieldSort("nameSort", SortOrder.Asc));
            case "name_desc" -> List.of(fieldSort("nameSort", SortOrder.Desc));
            default -> List.of();
        };
    }

    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(order)));
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
            clearSearchCache();

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
        clearSearchCache();

        log.info("Indexed single product {} into Elasticsearch", doc.getId());
    }

    public void deleteProductFromIndex(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        productSearchRepository.deleteById(id);
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();
        clearSearchCache();

        log.info("Deleted product {} from Elasticsearch index", id);
    }

    public void bootstrapIndexIfEmpty() {
        try {
            var indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
            if (indexOps.exists()) {
                long existingDocuments = productSearchRepository.count();
                if (existingDocuments > 0) {
                    log.info("Search index already contains {} products; skipping startup reindex", existingDocuments);
                    return;
                }
                log.info("Search index exists but is empty; running startup reindex");
            } else {
                log.info("Search index does not exist; running startup reindex");
            }

            int indexed = reindexProducts();
            log.info("Startup search reindex completed with {} products", indexed);
        } catch (Exception ex) {
            log.warn("Startup search index bootstrap failed; continuing with current index state", ex);
        }
    }

    private void clearSearchCache() {
        try {
            searchCache.clear();
        } catch (Exception ex) {
            log.warn("Failed to clear search cache after index mutation", ex);
        }
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
        String description = asString(p.get("description"));
        String brand = asString(p.get("brand"));

        BigDecimal price = asBigDecimal(p.get("price"));
        String currency = firstNonBlank(asString(p.get("currency")), "INR");
        Integer stockQuantity = asInteger(p.get("stockQuantity"));

        var imageUrls = asStringList(p.get("imageUrls"));

        String thumbnailUrl = firstNonBlank(
                asString(p.get("thumbnailUrl")),
                asString(p.get("imageUrl")),
                asString(p.get("image")),
                !imageUrls.isEmpty() ? imageUrls.get(0) : null
        );

        List<String> tags = asStringList(p.get("tags"));
        Map<String, String> attributes = asStringMap(p.get("attributes"));
        String searchText = buildSearchText(name, description, brand, category, tags, attributes);

        return new ProductDocument(
                id,
                slug,
                name,
                description,
                brand,
                category,
                asLong(id),
                price,
                currency,
                tags,
                thumbnailUrl,
                stockQuantity,
                attributes.get("color"),
                attributes.get("type"),
                attributes.get("fit"),
                attributes.get("storage"),
                attributes.get("memory"),
                attributes.get("material"),
                searchText,
                0L
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

    private static Integer asInteger(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o));
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

    private static Map<String, String> asStringMap(Object o) {
        if (!(o instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return map.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue()),
                        (left, right) -> left));
    }

    private static String buildSearchText(String name,
                                          String description,
                                          String brand,
                                          String category,
                                          List<String> tags,
                                          Map<String, String> attributes) {
        StringJoiner joiner = new StringJoiner(" ");
        addSearchPart(joiner, name);
        addSearchPart(joiner, description);
        addSearchPart(joiner, brand);
        addSearchPart(joiner, category);
        if (tags != null) {
            tags.forEach(tag -> addSearchPart(joiner, tag));
        }
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                addSearchPart(joiner, key);
                addSearchPart(joiner, value);
            });
        }
        return joiner.toString();
    }

    private static void addSearchPart(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(value);
        }
    }

    private static String normalized(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
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
