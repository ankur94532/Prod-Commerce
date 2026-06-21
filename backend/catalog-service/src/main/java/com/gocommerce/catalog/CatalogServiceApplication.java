// src/main/java/com/gocommerce/catalog/CatalogServiceApplication.java
package com.gocommerce.catalog;

import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import com.gocommerce.catalog.seed.ProductSeedCatalog;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.TimeZone;

@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner seedProducts(ProductRepository productRepository,
                                   @Value("${catalog.seed.size:1000}") int seedSize) {
        return args -> {
            int saved = 0;
            for (Product seedProduct : ProductSeedCatalog.products(seedSize)) {
                Product product = productRepository.findBySlug(seedProduct.getSlug())
                        .orElseGet(Product::new);

                product.setSlug(seedProduct.getSlug());
                product.setName(seedProduct.getName());
                product.setDescription(seedProduct.getDescription());
                product.setPrice(seedProduct.getPrice());
                product.setCurrency(seedProduct.getCurrency());
                product.setCategorySlug(seedProduct.getCategorySlug());
                product.setBrand(seedProduct.getBrand());
                product.setImageUrls(seedProduct.getImageUrls());
                product.setStockQuantity(seedProduct.getStockQuantity());
                product.setActive(seedProduct.isActive());
                product.setAttributes(seedProduct.getAttributes());

                productRepository.save(product);
                saved++;
            }

            System.out.println(">>> Seeded/updated " + saved + " products into catalog");
        };
    }
}
