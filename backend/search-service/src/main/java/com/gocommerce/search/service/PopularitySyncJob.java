package com.gocommerce.search.service;

import com.gocommerce.search.client.RecommendationClient;
import com.gocommerce.search.client.RecommendationClient.PopularityItem;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PopularitySyncJob {

    private static final Logger log = LoggerFactory.getLogger(PopularitySyncJob.class);

    private final ProductSearchRepository productSearchRepository;
    private final RecommendationClient recommendationClient;

    public PopularitySyncJob(ProductSearchRepository productSearchRepository,
                             RecommendationClient recommendationClient) {
        this.productSearchRepository = productSearchRepository;
        this.recommendationClient = recommendationClient;
    }

    /**
     * Periodically sync popularity scores from recommendation-service into Elasticsearch.
     * Default: every 10 minutes (600_000 ms).
     */
    @Scheduled(fixedDelayString = "${search.popularity-sync.interval-ms:600000}")
    public void syncPopularity() {
        List<PopularityItem> items = recommendationClient.fetchPopularity();
        if (items.isEmpty()) {
            log.debug("Popularity sync: no items received.");
            return;
        }

        List<ProductDocument> docsToSave = new ArrayList<>();

        for (PopularityItem item : items) {
            String productId = item.productId();
            if (productId == null || productId.isBlank()) {
                continue;
            }

            productSearchRepository.findById(productId).ifPresent(doc -> {
                Long score = item.totalQuantity(); // simplicity: popularity = totalQuantity
                if (score == null) score = 0L;
                doc.setPopularityScore(score);
                docsToSave.add(doc);
            });
        }

        if (!docsToSave.isEmpty()) {
            productSearchRepository.saveAll(docsToSave);
            log.info("Popularity sync: updated popularityScore on {} products", docsToSave.size());
        } else {
            log.debug("Popularity sync: no matching products to update");
        }
    }
}
