package com.gocommerce.catalog.seed;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSeedCatalogTest {

    @Test
    void products_containsOneThousandProductsWithUniqueSlugs() {
        var products = ProductSeedCatalog.products();
        var slugs = products.stream()
                .map(product -> product.getSlug())
                .collect(Collectors.toSet());

        assertThat(products).hasSize(1_000);
        assertThat(slugs).hasSize(1_000);
        assertThat(products)
                .allSatisfy(product -> assertThat(product.getImageUrls())
                        .isNotNull()
                        .isNotEmpty()
                        .allSatisfy(imageUrl -> assertThat(imageUrl).startsWith("https://")));
    }

    @Test
    void products_withTargetCountExpandsDeterministically() {
        var products = ProductSeedCatalog.products(50_000);
        var slugs = products.stream()
                .map(product -> product.getSlug())
                .collect(Collectors.toSet());

        assertThat(products).hasSize(50_000);
        assertThat(slugs).hasSize(50_000);
    }

    @Test
    void products_withSmallTargetCountReturnsExactCount() {
        assertThat(ProductSeedCatalog.products(10)).hasSize(10);
    }
}
