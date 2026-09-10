package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.client.CatalogClient.CatalogProductPage;
import com.gocommerce.search.config.SearchProperties;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.RetrievalInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.dto.SearchDtos.SearchResultItem;
import com.gocommerce.search.metrics.SearchMetrics;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import co.elastic.clients.json.JsonData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    /** Names the cardinality aggregation that counts families behind a collapsed page. */
    private static final String GROUP_COUNT_AGGREGATION = "collapsed_group_count";
    /**
     * Fallback family derivation for documents whose catalog does not publish one: the seed
     * generator names variants "&lt;base slug&gt;-&lt;descriptor&gt;-&lt;n&gt;". This is a
     * heuristic and mis-groups genuine model names of the same shape, so it is used only
     * when the catalog says nothing.
     */
    private static final Pattern VARIANT_SLUG_SUFFIX = Pattern.compile("^(.*)-[a-z]{3,}-\\d{1,3}$");
    private static final Set<String> KNOWN_BRANDS = Set.of(
            "acer", "allen solly", "amazon", "amazon basics", "amazon essentials", "amazfit", "and",
            "apple", "arrow", "asus", "aurelia", "bajaj", "bewakoof", "boat", "boldfit", "borosil",
            "catwalk", "cello", "classmate", "converse", "crocs", "dell", "dennis lingo",
            "eureka forbes", "fitbit", "garmin", "garnier", "gear", "google", "harpa", "h&m",
            "highlander", "hp", "hrx", "instant", "jbl", "kore", "lavie", "lenovo", "levi's",
            "logitech", "mango", "marks & spencer", "maybelline", "milton", "minimalist",
            "motorola", "msi", "nike", "nivia", "nothing", "only", "oneplus", "orient",
            "penguin", "pentonic", "peter england", "philips", "portronics", "prestige", "puma",
            "red tape", "reddragon", "redmi", "roadster", "samsung", "skechers", "skybags",
            "solimo", "sony", "soundcore", "stuffcool", "symbol", "the souled store",
            "tokyo talkies", "u.s. polo assn.", "van heusen", "vero moda", "wildhorn", "woodland",
            "wrangler", "yonex");
    private static final List<CategoryIntent> CATEGORY_INTENTS = List.of(
            new CategoryIntent("bags-wallets", List.of(
                    "laptop backpack", "backpack", "wallet", "sling bag", "tote bag", "crossbody bag",
                    "travel bag", "bag", "bags")),
            new CategoryIntent("accessories-cables", List.of(
                    "phone stand", "charger", "cable", "usb cable", "usb-c cable", "keyboard", "mouse",
                    "adapter", "accessory", "accessories")),
            new CategoryIntent("smartphones", List.of(
                    "mobile", "mobiles", "phone", "phones", "smartphone", "smartphones", "android phone",
                    "iphone", "5g phone", "cell phone", "handset")),
            new CategoryIntent("laptops", List.of(
                    "gaming laptop", "work laptop", "student laptop", "laptop", "laptops", "ultrabook")),
            new CategoryIntent("tablets", List.of(
                    "android tablet", "e reader", "tablet", "tablets", "ipad", "kindle")),
            new CategoryIntent("earbuds-headphones", List.of(
                    "wireless earbuds", "bluetooth headphones", "noise cancelling headphones", "earbuds",
                    "headphones", "earphones", "headset")),
            new CategoryIntent("watches-wearables", List.of(
                    "smart watch", "sports watch", "fitness band", "smartwatch", "wearable", "watch", "watches")),
            new CategoryIntent("footwear", List.of(
                    "running shoes", "walking shoes", "formal shoes", "flip flops", "shoes", "shoe",
                    "sneakers", "sandals", "heels")),
            new CategoryIntent("mens-shirts", List.of(
                    "shirt for men", "men shirt", "mens shirt", "formal shirt", "casual shirt")),
            new CategoryIntent("mens-tshirts", List.of(
                    "oversized tshirt", "cotton tshirt", "men tshirt", "mens tshirt", "t shirt", "tshirt")),
            new CategoryIntent("mens-jeans-trousers", List.of(
                    "men jeans", "mens jeans", "jeans", "trousers", "chinos", "pants")),
            new CategoryIntent("womens-dresses", List.of(
                    "women dress", "maxi dress", "party dress", "dress", "dresses")),
            new CategoryIntent("womens-tops", List.of(
                    "women top", "kurti", "blouse", "top", "tops")),
            new CategoryIntent("home-appliances", List.of(
                    "home appliance", "air fryer", "washing machine", "refrigerator", "appliance", "fan",
                    "mixer", "vacuum")),
            new CategoryIntent("kitchen-dining", List.of(
                    "lunch box", "pressure cooker", "water bottle", "cookware", "kitchen", "dining",
                    "bottle", "tawa", "flask")),
            new CategoryIntent("fitness-sports", List.of(
                    "yoga mat", "badminton", "football", "cricket", "dumbbell", "sports", "fitness", "gym")),
            new CategoryIntent("beauty-grooming", List.of(
                    "face wash", "beauty", "grooming", "lipstick", "serum", "shampoo", "trimmer", "sunscreen")),
            new CategoryIntent("books-stationery", List.of(
                    "notebook", "notebooks", "stationery", "planner", "book", "books", "pen", "paper"))
    );

    private final ProductSearchRepository productSearchRepository;
    private final SearchCache searchCache;
    private final CatalogClient catalogClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchMetrics searchMetrics;
    private final ProductEmbeddingService embeddingService;
    private final SearchProperties searchProperties;
    private final int indexingBatchSize;
    private final SearchIndexManager indexManager;

    /** Rebuild leftovers older than this are cleaned up; long enough not to race a live rebuild. */
    private static final long ORPHANED_INDEX_RETENTION_MS = java.time.Duration.ofHours(6).toMillis();

    @Autowired
    public SearchService(ProductSearchRepository productSearchRepository,
                         SearchCache searchCache,
                         CatalogClient catalogClient,
                         ElasticsearchOperations elasticsearchOperations,
                         SearchMetrics searchMetrics,
                         ProductEmbeddingService embeddingService,
                         SearchProperties searchProperties) {
        this.productSearchRepository = productSearchRepository;
        this.searchCache = searchCache;
        this.catalogClient = catalogClient;
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchMetrics = searchMetrics;
        this.embeddingService = embeddingService;
        this.searchProperties = searchProperties != null ? searchProperties : new SearchProperties();
        this.indexingBatchSize = Math.max(1, this.searchProperties.getIndexing().getBatchSize());
        this.indexManager = elasticsearchOperations != null ? new SearchIndexManager(elasticsearchOperations) : null;
    }

    public SearchService(ProductSearchRepository productSearchRepository,
                         SearchCache searchCache,
                         CatalogClient catalogClient,
                         ElasticsearchOperations elasticsearchOperations,
                         SearchMetrics searchMetrics) {
        this(productSearchRepository, searchCache, catalogClient, elasticsearchOperations, searchMetrics,
                new HashingProductEmbeddingService(), new SearchProperties());
    }

    public SearchService(ProductSearchRepository productSearchRepository,
                         SearchCache searchCache,
                         CatalogClient catalogClient,
                         ElasticsearchOperations elasticsearchOperations,
                         SearchMetrics searchMetrics,
                         ProductEmbeddingService embeddingService) {
        this(productSearchRepository, searchCache, catalogClient, elasticsearchOperations, searchMetrics,
                embeddingService, new SearchProperties());
    }

    // overload for tests that were using 4-arg ctor
    public SearchService(ProductSearchRepository productSearchRepository,
                         SearchCache searchCache,
                         CatalogClient catalogClient,
                         ElasticsearchOperations elasticsearchOperations) {
        this(productSearchRepository, searchCache, catalogClient, elasticsearchOperations, null,
                new HashingProductEmbeddingService(), new SearchProperties());
    }

    @CircuitBreaker(name = "searchCore", fallbackMethod = "searchFallback")
    @Retry(name = "searchCore")
    public SearchResponse search(SearchRequest request) {
        String query = request.query();
        String categoryFilter = request.category();
        int page = request.page() != null ? Math.max(request.page(), 0) : 0;
        int size = request.size() != null ? Math.max(1, Math.min(request.size(), 100)) : 20;
        String mode = normalizedMode(request.mode());
        String sort = normalized(request.sort());
        if (sort != null && !Set.of("relevance", "price_asc", "price_desc", "name_asc", "name_desc", "newest").contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort");
        }
        if ("hybrid_rrf".equals(mode) && sort != null && !"relevance".equals(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hybrid_rrf supports relevance sorting only");
        }
        if (request.minPrice() != null && request.maxPrice() != null && request.minPrice().compareTo(request.maxPrice()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minPrice must not exceed maxPrice");
        }
        if ((long) page * size + size > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search page exceeds supported result window");
        }

        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0, page, size, 0, retrievalInfo(mode, page, size));
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
                sort,
                mode,
                page,
                size);

        var cached = searchCache.get(normalizedRequest);
        if (cached.isPresent() && retrievalInfo(normalizedRequest).equals(cached.get().retrieval())) {
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
        // Invalid requests retain their status; dependency failures must not masquerade as zero hits.
        if (ex instanceof ResponseStatusException status) throw status;
        log.warn("Search unavailable: {}", ex.getClass().getSimpleName());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Search temporarily unavailable", ex);
    }


    private SearchResponse doSearchAndCache(SearchRequest normalizedRequest) {
        if ("hybrid_rrf".equals(normalizedRequest.mode())) return doRrfSearchAndCache(normalizedRequest);
        if (isVectorMode(normalizedRequest.mode())) {
            return doVectorSearchAndCache(normalizedRequest);
        }
        if (isHybridMode(normalizedRequest.mode())) {
            return doHybridSearchAndCache(normalizedRequest);
        }
        return doKeywordSearchAndCache(normalizedRequest);
    }

    private SearchResponse doKeywordSearchAndCache(SearchRequest normalizedRequest) {
        int page = normalizedRequest.page() != null ? normalizedRequest.page() : 0;
        int size = normalizedRequest.size() != null ? normalizedRequest.size() : 20;

        var queryBuilder = NativeQuery.builder()
                .withQuery(buildSearchQuery(normalizedRequest))
                .withPageable(PageRequest.of(page, size))
                .withTrackTotalHits(true);
        applyCollapse(queryBuilder);

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

        long total = resultTotal(searchHits);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        SearchResponse response = new SearchResponse(items, total, page, size, totalPages, retrievalInfo(normalizedRequest));
        searchCache.put(normalizedRequest, response);

        return response;
    }

    private Query buildSearchQuery(SearchRequest request) {
        List<Query> filters = buildFilters(request);

        return Query.of(q -> q.bool(b -> {
            b.must(buildTextQuery(request.query()));
            if (!filters.isEmpty()) {
                b.filter(filters);
            }
            return b;
        }));
    }

    private Query buildTextQuery(String query) {
        String expandedQuery = expandQuery(query);
        String categoryIntent = detectCategoryIntent(query);

        return Query.of(q -> q.bool(b -> {
            b.should(Query.of(s -> s.matchPhrase(mp -> mp
                    .field("name")
                    .query(query)
                    .boost(6.0f))));
            b.should(Query.of(s -> s.match(m -> m
                    .field("name")
                    .query(query)
                    .operator(Operator.And)
                    .boost(4.0f))));
            b.should(Query.of(s -> s.match(m -> m
                    .field("name")
                    .query(query)
                    .operator(Operator.And)
                    .fuzziness("AUTO")
                    .boost(3.0f))));
            b.should(Query.of(s -> s.match(m -> m
                    .field("description")
                    .query(query)
                    .operator(Operator.And)
                    .boost(2.0f))));
            b.should(Query.of(s -> s.match(m -> m
                    .field("searchText")
                    .query(query)
                    .operator(Operator.And)
                    .boost(2.0f))));
            b.should(Query.of(s -> s.match(m -> m
                    .field("searchText")
                    .query(expandedQuery)
                    .operator(Operator.Or)
                    .boost(1.5f))));
            // Fuzziness previously applied to the name field alone, with AND semantics. A
            // misspelled query whose words straddle the name and the description therefore
            // matched nothing at all: "rechargable trimer" missed "Cordless Beard Trimmer"
            // because only "trimmer" is in the name and only "rechargeable" is in the
            // description. searchText concatenates the fields, so fuzzy matching belongs
            // here. minimumShouldMatch keeps a single loose term from dragging in the
            // catalog, and the low boost keeps corrections below exact matches.
            b.should(Query.of(s -> s.match(m -> m
                    .field("searchText")
                    .query(query)
                    .operator(Operator.Or)
                    .fuzziness("AUTO")
                    .minimumShouldMatch("2<70%")
                    .boost(1.0f))));
            for (String brand : detectBrandIntents(query)) {
                b.should(Query.of(s -> s.term(t -> t
                        .field("brand")
                        .value(brand)
                        .caseInsensitive(true)
                        .boost(18.0f))));
            }
            if (categoryIntent != null) {
                b.should(Query.of(s -> s.term(t -> t
                        .field("category")
                        .value(categoryIntent)
                        .boost(25.0f))));
            }
            return b.minimumShouldMatch("1");
        }));
    }

    private SearchResponse doHybridSearchAndCache(SearchRequest normalizedRequest) {
        int page = normalizedRequest.page() != null ? normalizedRequest.page() : 0;
        int size = normalizedRequest.size() != null ? normalizedRequest.size() : 20;
        List<Float> queryVector = embeddingService.embed(normalizedRequest.query());

        validateQueryVector(queryVector);

        var hybridBuilder = NativeQuery.builder()
                .withQuery(buildHybridQuery(normalizedRequest, queryVector))
                .withSort(sortOptions(normalizedRequest.sort()))
                .withPageable(PageRequest.of(page, size))
                .withTrackTotalHits(true);
        applyCollapse(hybridBuilder);

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(
                hybridBuilder.build(), ProductDocument.class);

        List<SearchResultItem> items = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toResultItem)
                .toList();

        if (searchMetrics != null && items.isEmpty()) {
            searchMetrics.incrementZeroResult();
        }

        long total = resultTotal(searchHits);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        SearchResponse response = new SearchResponse(items, total, page, size, totalPages, retrievalInfo(normalizedRequest));
        searchCache.put(normalizedRequest, response);

        return response;
    }

    private SearchResponse doVectorSearchAndCache(SearchRequest normalizedRequest) {
        int page = normalizedRequest.page() != null ? normalizedRequest.page() : 0;
        int size = normalizedRequest.size() != null ? normalizedRequest.size() : 20;
        List<Float> queryVector = embeddingService.embed(normalizedRequest.query());

        validateQueryVector(queryVector);

        var queryBuilder = NativeQuery.builder()
                .withSort(sortOptions(normalizedRequest.sort()))
                .withPageable(PageRequest.of(page, size))
                .withTrackTotalHits(true);
        if ("vector_exact".equals(normalizedRequest.mode())) {
            queryBuilder.withQuery(buildExactVectorQuery(normalizedRequest, queryVector));
        } else {
            queryBuilder.withKnnQuery(buildAnnVectorQuery(normalizedRequest, queryVector, page, size));
        }
        applyCollapse(queryBuilder);

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(
                queryBuilder.build(), ProductDocument.class);

        List<SearchResultItem> items = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toResultItem)
                .toList();

        if (searchMetrics != null && items.isEmpty()) {
            searchMetrics.incrementZeroResult();
        }

        long total = resultTotal(searchHits);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        SearchResponse response = new SearchResponse(items, total, page, size, totalPages, retrievalInfo(normalizedRequest));
        searchCache.put(normalizedRequest, response);

        return response;
    }

    private Query buildExactVectorQuery(SearchRequest request, List<Float> queryVector) {
        List<Query> filters = buildFilters(request);
        filters.add(Query.of(q -> q.exists(e -> e.field("searchEmbedding"))));

        Query baseQuery = Query.of(q -> q.bool(b -> {
            b.filter(filters);
            return b;
        }));

        return Query.of(q -> q.scriptScore(ss -> ss
                .query(baseQuery)
                .script(s -> s.inline(i -> i
                        .lang(ScriptLanguage.Painless)
                        .source("cosineSimilarity(params.queryVector, 'searchEmbedding') + 1.0")
                        .params("queryVector", JsonData.of(queryVector))))));
    }

    private KnnQuery buildAnnVectorQuery(SearchRequest request, List<Float> queryVector, int page, int size) {
        List<Query> filters = buildFilters(request);
        return KnnQuery.of(knn -> knn
                .field("searchEmbedding")
                .queryVector(queryVector)
                .numCandidates((long) annCandidates(page, size))
                .filter(filters));
    }

    private Query buildHybridQuery(SearchRequest request, List<Float> queryVector) {
        List<Query> filters = buildFilters(request);
        filters.add(Query.of(q -> q.exists(e -> e.field("searchEmbedding"))));

        Query baseQuery = Query.of(q -> q.bool(b -> {
            b.must(buildTextQuery(request.query()));
            b.filter(filters);
            return b;
        }));

        double keywordWeight = searchProperties.getHybrid().getKeywordWeight();
        double vectorWeight = searchProperties.getHybrid().getVectorWeight();

        return Query.of(q -> q.scriptScore(ss -> ss
                .query(baseQuery)
                .script(s -> s.inline(i -> i
                        .lang(ScriptLanguage.Painless)
                        .source("(_score * params.keywordWeight) + ((cosineSimilarity(params.queryVector, 'searchEmbedding') + 1.0) * params.vectorWeight)")
                        .params("queryVector", JsonData.of(queryVector))
                        .params("keywordWeight", JsonData.of(keywordWeight))
                        .params("vectorWeight", JsonData.of(vectorWeight))))));
    }

    private List<Query> buildFilters(SearchRequest request) {
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
        return filters;
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
        SortOptions primary = switch (sort == null ? "relevance" : sort) {
            case "newest" -> fieldSort("productId", SortOrder.Desc);
            case "price_asc" -> fieldSort("price", SortOrder.Asc);
            case "price_desc" -> fieldSort("price", SortOrder.Desc);
            case "name_asc" -> fieldSort("nameSort", SortOrder.Asc);
            case "name_desc" -> fieldSort("nameSort", SortOrder.Desc);
            default -> SortOptions.of(s -> s.score(score -> score.order(SortOrder.Desc)));
        };
        // Numeric catalog IDs are unique; slug also stabilizes documents imported without a numeric ID.
        return List.of(primary, fieldSort("productId", SortOrder.Asc), fieldSort("slug", SortOrder.Asc));
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
        return reindexProductsDetailed().indexed();
    }

    @Transactional
    public ReindexResult reindexProductsDetailed() {
        // Build into a new index that nothing reads. The live alias is only moved once the
        // rebuild is complete and consistent, so a failure here leaves search serving the
        // previous index rather than an empty one.
        String buildIndex = null;
        try {
            boolean migratingLegacyIndex = indexManager.hasLegacyConcreteIndex();
            buildIndex = indexManager.createIndex();

            int indexed = 0;
            int catalogProducts = 0;
            int page = 0;
            int totalPages = 1;
            long expectedCatalogProducts = 0L;

            do {
                CatalogProductPage productPage = catalogClient.fetchProductsPage(page, CatalogClient.DEFAULT_PAGE_SIZE);
                List<Map<String, Object>> products = productPage.products();
                totalPages = Math.max(productPage.totalPages(), page + 1);
                expectedCatalogProducts = Math.max(expectedCatalogProducts, productPage.totalElements());
                catalogProducts += products.size();

                for (int start = 0; start < products.size(); start += indexingBatchSize) {
                    int end = Math.min(start + indexingBatchSize, products.size());
                    List<ProductDocument> docs = toDocuments(products.subList(start, end));
                    if (!docs.isEmpty()) {
                        elasticsearchOperations.save(docs,
                                org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.of(buildIndex));
                        indexed += docs.size();
                    }
                }

                long expectedTotal = expectedCatalogProducts > 0 ? expectedCatalogProducts : catalogProducts;
                log.info("Reindex progress: indexed {}/{} catalog products (page {}/{})",
                        indexed, expectedTotal, page + 1, totalPages);
                page++;
            } while (page < totalPages);

            indexManager.refresh(buildIndex);
            long indexedDocuments = indexManager.count(buildIndex);

            ReindexResult result = ReindexResult.success(indexed, catalogProducts, indexedDocuments,
                    expectedCatalogProducts);
            if (!result.consistent()) {
                // Publishing an index we already know disagrees with the catalog would
                // replace working results with wrong ones. Keep serving the old index.
                log.error("Refusing to publish an inconsistent rebuild: catalogProducts={}, indexed={}, "
                        + "indexedDocuments={}, catalogDeclaredTotal={}. The live index is unchanged.",
                        catalogProducts, indexed, indexedDocuments, expectedCatalogProducts);
                indexManager.dropIndex(buildIndex);
                if (searchMetrics != null) {
                    searchMetrics.onReindexCompleted(0);
                }
                return result;
            }

            if (migratingLegacyIndex) {
                indexManager.replaceLegacyIndex(buildIndex);
            } else {
                for (String retired : indexManager.promote(buildIndex)) {
                    indexManager.dropIndex(retired);
                }
            }
            indexManager.dropOrphanedIndices(buildIndex, ORPHANED_INDEX_RETENTION_MS);
            clearSearchCache();

            log.info("Reindexed {} products into {} and promoted it", indexed, buildIndex);
            if (searchMetrics != null) {
                searchMetrics.onReindexCompleted(indexed);
            }

            return result;
        } catch (Exception ex) {
            // The alias was never moved, so search keeps serving whatever it served before.
            log.error("Reindex failed; the live search index is unchanged", ex);
            if (buildIndex != null) {
                indexManager.dropIndex(buildIndex);
            }
            if (searchMetrics != null) {
                searchMetrics.onReindexCompleted(0);
            }
            return ReindexResult.failure(ex.getMessage());
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

    private List<ProductDocument> toDocuments(List<Map<String, Object>> products) {
        List<ProductDocument> docs = new ArrayList<>();
        List<String> embeddingTexts = new ArrayList<>();
        for (Map<String, Object> product : products) {
            ProductDocument doc = toDocumentWithoutEmbedding(product);
            if (doc.getId() == null || doc.getId().isBlank()) {
                continue;
            }
            docs.add(doc);
            embeddingTexts.add(firstNonBlank(doc.getSearchText(), ""));
        }

        List<List<Float>> embeddings = embeddingService.embedAll(embeddingTexts);
        if (embeddings.size() != docs.size()) {
            throw new IllegalStateException("Embedding count mismatch: expected " + docs.size() + " but got " + embeddings.size());
        }
        for (int i = 0; i < docs.size(); i++) {
            docs.get(i).setSearchEmbedding(embeddings.get(i));
        }
        return docs;
    }

    private ProductDocument toDocument(Map<String, Object> p) {
        ProductDocument document = toDocumentWithoutEmbedding(p);
        document.setSearchEmbedding(embeddingService.embed(firstNonBlank(document.getSearchText(), "")));
        return document;
    }

    private ProductDocument toDocumentWithoutEmbedding(Map<String, Object> p) {
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
        String searchText = firstNonBlank(
                asString(p.get("embeddingText")),
                buildSearchText(name, description, brand, category, tags, attributes));

        ProductDocument document = new ProductDocument(
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
        document.setProductFamily(productFamily(p, slug, id));
        return document;
    }

    /**
     * The family a document collapses into.
     *
     * The catalog publishes it, and that is the answer whenever it is there. The fallback
     * exists for documents indexed from a catalog that predates the column: variant slugs
     * were generated as "&lt;base slug&gt;-&lt;descriptor&gt;-&lt;n&gt;", so that suffix is
     * stripped. It is a guess, and a wrong one for model names of the same shape --
     * "sony-wh-1000" is a product, not variant 1000 of "sony-wh" -- which is why it never
     * overrides what the catalog says. A wrong guess makes a product its own family, so it
     * costs a missed grouping and never merges two different products.
     */
    private static String productFamily(Map<String, Object> product, String slug, String id) {
        String published = firstNonBlank(
                asString(product.get("productFamily")),
                asString(product.get("family")),
                asString(product.get("familySlug")));
        if (published != null) {
            return published;
        }
        if (slug != null) {
            Matcher matcher = VARIANT_SLUG_SUFFIX.matcher(slug);
            if (matcher.matches() && !matcher.group(1).isBlank()) {
                return matcher.group(1);
            }
            return slug;
        }
        return id;
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

    private static String expandQuery(String query) {
        String normalized = normalized(query);
        if (normalized == null) {
            return query;
        }

        String category = detectCategoryIntent(normalized);
        return category != null ? normalized + " " + categoryExpansion(category) : normalized;
    }

    private static String detectCategoryIntent(String query) {
        String normalized = normalized(query);
        if (normalized == null) {
            return null;
        }
        String tokenizedQuery = tokenized(normalized);
        for (CategoryIntent intent : CATEGORY_INTENTS) {
            for (String alias : intent.aliases()) {
                if (tokenizedQuery.contains(tokenized(alias))) {
                    return intent.category();
                }
            }
        }
        return null;
    }

    private static Set<String> detectBrandIntents(String query) {
        String normalized = normalized(query);
        if (normalized == null) {
            return Set.of();
        }

        String tokenizedQuery = tokenized(normalized);
        Set<String> matches = new LinkedHashSet<>();
        for (String brand : KNOWN_BRANDS) {
            String tokenizedBrand = tokenized(brand);
            if ("and".equals(brand)) {
                String lower = normalized.toLowerCase();
                if (lower.equals("and") || lower.startsWith("and ")) {
                    matches.add("AND");
                }
                continue;
            }
            if (tokenizedQuery.contains(tokenizedBrand)) {
                matches.add(brand);
            }
        }
        return matches;
    }

    private static String categoryExpansion(String category) {
        return switch (category) {
            case "smartphones" -> "phone phones smartphone smartphones mobile mobiles handset";
            case "laptops" -> "laptop laptops ultrabook gaming notebook computer";
            case "tablets" -> "tablet tablets ipad kindle e-reader";
            case "earbuds-headphones" -> "earbuds headphones earphones headset audio";
            case "watches-wearables" -> "watch watches smartwatch wearable fitness band";
            case "footwear" -> "shoes shoe sneakers footwear sandals heels";
            case "bags-wallets" -> "bag bags backpack wallet tote sling crossbody";
            case "mens-shirts" -> "men mens shirt shirts formal casual";
            case "mens-tshirts" -> "men mens tshirt t-shirt tee cotton";
            case "mens-jeans-trousers" -> "men mens jeans trousers chinos pants";
            case "womens-dresses" -> "women womens dress dresses maxi party";
            case "womens-tops" -> "women womens top tops kurti blouse";
            case "home-appliances" -> "home appliance appliances fan mixer vacuum refrigerator";
            case "kitchen-dining" -> "kitchen dining bottle cookware lunch box flask";
            case "fitness-sports" -> "fitness sports gym yoga badminton football cricket";
            case "beauty-grooming" -> "beauty grooming lipstick serum shampoo trimmer sunscreen";
            case "books-stationery" -> "book books stationery notebook pen planner paper";
            case "accessories-cables" -> "accessory accessories charger cable keyboard mouse adapter stand";
            default -> "";
        };
    }

    private static String tokenized(String value) {
        String normalized = normalized(value);
        if (normalized == null) {
            return " ";
        }
        return " " + normalized.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    private static String normalizedMode(String value) {
        String normalized = normalized(value);
        if (normalized == null || "hybrid".equalsIgnoreCase(normalized)) {
            return "hybrid";
        }
        if ("keyword".equalsIgnoreCase(normalized) || "text".equalsIgnoreCase(normalized)) {
            return "text";
        }
        if ("vector".equalsIgnoreCase(normalized) || "semantic".equalsIgnoreCase(normalized)) {
            return "vector";
        }
        if ("vector_exact".equalsIgnoreCase(normalized)) return "vector_exact";
        if ("hybrid_rrf".equalsIgnoreCase(normalized)) return "hybrid_rrf";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported retrieval mode");
    }

    private static boolean isVectorMode(String value) {
        return "vector".equalsIgnoreCase(value) || "vector_exact".equalsIgnoreCase(value);
    }

    private static boolean isHybridMode(String value) {
        return "hybrid".equalsIgnoreCase(value);
    }

    private void validateQueryVector(List<Float> vector) {
        if (vector == null || vector.size() != ProductDocument.SEARCH_EMBEDDING_DIMENSIONS
                || vector.stream().anyMatch(v -> v == null || !Float.isFinite(v))
                || vector.stream().allMatch(v -> v == 0.0f)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Embedding unavailable or invalid");
        }
    }

    private RetrievalInfo retrievalInfo(SearchRequest request) {
        return retrievalInfo(request.mode(), request.page(), request.size());
    }

    private RetrievalInfo retrievalInfo(String mode, int page, int size) {
        String collapse = collapseField();
        return switch (mode) {
            case "hybrid_rrf" -> new RetrievalInfo(mode, "rrf_union_hnsw_vector",
                    totalRelation("candidate_union", "collapsed_candidate_union"),
                    searchProperties.getRrf().getCandidateWindow(), searchProperties.getRrf().getRankConstant(), 0, 0,
                    collapse);
            case "vector" -> new RetrievalInfo(mode, "hnsw_cosine",
                    totalRelation("ann_candidates", "collapsed_ann_groups_approximate"),
                    annCandidates(page, size), 0, 0, 1, collapse);
            case "vector_exact" -> new RetrievalInfo(mode, "exact_cosine",
                    totalRelation("exact", "collapsed_groups_approximate"), 0, 0, 0, 1, collapse);
            case "hybrid" -> new RetrievalInfo(mode, "lexically_gated_weighted_cosine",
                    totalRelation("exact", "collapsed_groups_approximate"), 0, 0,
                    searchProperties.getHybrid().getKeywordWeight(), searchProperties.getHybrid().getVectorWeight(),
                    collapse);
            default -> new RetrievalInfo("text", "lexical_with_rules",
                    totalRelation("exact", "collapsed_groups_approximate"), 0, 0, 1, 0, collapse);
        };
    }

    private String totalRelation(String uncollapsed, String collapsed) {
        return collapseField() == null ? uncollapsed : collapsed;
    }

    /** The field result pages collapse on, or null when collapsing is switched off. */
    private String collapseField() {
        SearchProperties.Collapse collapse = searchProperties.getCollapse();
        return collapse.isEnabled() ? collapse.getField() : null;
    }

    /**
     * Collapses a result page to one hit per product family, and asks Elasticsearch for the
     * number of families alongside it.
     *
     * The count matters as much as the collapsing. Collapsed hits are drawn from the same
     * matching set, so {@code totalHits} still counts documents: reporting it would tell a
     * client there are 600 results and 30 pages when 113 results across 6 pages exist, and
     * every page past the sixth would come back empty.
     */
    private void applyCollapse(NativeQueryBuilder builder) {
        if (collapseHits(builder)) {
            String field = collapseField();
            builder.withAggregation(GROUP_COUNT_AGGREGATION, Aggregation.of(a -> a.cardinality(c -> c.field(field))));
        }
    }

    /**
     * Collapsing alone, for the reciprocal-rank-fusion legs: their totals come from the size
     * of the fused union, so a family count per leg would be paid for and thrown away.
     */
    private boolean collapseHits(NativeQueryBuilder builder) {
        String field = collapseField();
        if (field == null) {
            return false;
        }
        builder.withFieldCollapse(FieldCollapse.of(f -> f.field(field)));
        return true;
    }

    /**
     * The number of results the caller can actually page through: families when collapsing
     * is on, documents otherwise.
     *
     * Cardinality is approximate above Elasticsearch's precision threshold, which is why the
     * retrieval metadata says so rather than presenting the number as exact. A missing
     * aggregation is a hard failure: silently falling back to the document total would
     * restore exactly the pagination bug collapsing introduced.
     */
    private long resultTotal(SearchHits<ProductDocument> hits) {
        if (collapseField() == null) {
            return hits.getTotalHits();
        }
        if (hits.getAggregations() instanceof ElasticsearchAggregations aggregations) {
            var aggregation = aggregations.get(GROUP_COUNT_AGGREGATION);
            if (aggregation != null && aggregation.aggregation().getAggregate().isCardinality()) {
                return aggregation.aggregation().getAggregate().cardinality().value();
            }
        }
        throw new IllegalStateException(
                "Collapsed search returned no " + GROUP_COUNT_AGGREGATION + " aggregation; the family count "
                        + "behind this page is unknown and the document total would overstate it");
    }

    /**
     * The identity a result occupies in a collapsed page. Documents indexed before the
     * catalog published a family fall back to their own id, which makes them a group of one
     * rather than silently merging them all together.
     */
    private String groupKey(ProductDocument document) {
        if (collapseField() == null) {
            return document.getId();
        }
        String family = document.getProductFamily();
        return family != null && !family.isBlank() ? family : document.getId();
    }

    private int annCandidates(int page, int size) {
        long requestedWindow = ((long) page + 1) * size;
        return (int) Math.min(10000, Math.max(searchProperties.getAnn().getNumCandidates(), requestedWindow));
    }

    private SearchResponse doRrfSearchAndCache(SearchRequest request) {
        List<Float> vector = embeddingService.embed(request.query());
        validateQueryVector(vector);
        int window = searchProperties.getRrf().getCandidateWindow();
        int rankConstant = searchProperties.getRrf().getRankConstant();
        // Independent retrieval with identical filters. Vector-only candidates can enter the union.
        // Both legs collapse, so the candidate window buys window distinct products rather
        // than window colours of the same few.
        var lexicalBuilder = NativeQuery.builder().withQuery(buildSearchQuery(request))
                .withPageable(PageRequest.of(0, window)).withSort(sortOptions(null));
        var semanticBuilder = NativeQuery.builder()
                .withKnnQuery(buildAnnVectorQuery(request, vector, 0, window))
                .withPageable(PageRequest.of(0, window)).withSort(sortOptions(null));
        collapseHits(lexicalBuilder);
        collapseHits(semanticBuilder);
        var lexical = elasticsearchOperations.search(lexicalBuilder.build(), ProductDocument.class);
        var semantic = elasticsearchOperations.search(semanticBuilder.build(), ProductDocument.class);
        var documents = new java.util.HashMap<String, ProductDocument>();
        var scores = new java.util.HashMap<String, Double>();
        for (var hits : List.of(lexical, semantic)) {
            var seen = new java.util.HashSet<String>();
            int rank = 0;
            for (var hit : hits.getSearchHits()) {
                rank++;
                ProductDocument doc = hit.getContent();
                // Fused on the group, not the document id. The two legs collapse
                // independently and can pick different variants as the representative of
                // one family; keying on the id would put both in the union and undo the
                // collapsing at exactly the point the union is built.
                String key = groupKey(doc);
                if (seen.add(key)) {
                    documents.putIfAbsent(key, doc);
                    scores.merge(key, 1.0 / (rankConstant + rank), Double::sum);
                }
            }
        }
        var rankedIds = scores.keySet().stream().sorted(java.util.Comparator
                .<String>comparingDouble(scores::get).reversed().thenComparing(java.util.Comparator.naturalOrder())).toList();
        int page = request.page();
        int size = request.size();
        var items = rankedIds.stream().skip((long) page * size).limit(size)
                .map(documents::get).map(this::toResultItem).toList();
        int total = rankedIds.size();
        var response = new SearchResponse(items, total, page, size, (total + size - 1) / size, retrievalInfo(request));
        if (searchMetrics != null && items.isEmpty()) searchMetrics.incrementZeroResult();
        searchCache.put(request, response);
        return response;
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

    private record CategoryIntent(String category, List<String> aliases) {
    }
}
