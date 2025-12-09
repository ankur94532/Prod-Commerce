package com.gocommerce.catalog;

import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner seedProducts(ProductRepository productRepository) {
        return args -> {
            long count = productRepository.count();
            if (count > 0) {
                return;
            }

            Product phone1 = new Product(
                "s26-ultra-256gb-gray",
                "Galaxy S26 Ultra 256GB (Gray)",
                "Flagship smartphone with high-end camera and display.",
                new BigDecimal("89999"),
                "INR",
                "smartphones",
                "Samsung",
                List.of("https://example.com/images/s26-ultra-256-gray-1.jpg"),
                50,
                true,
                Map.of("storage", "256GB", "color", "Gray")
            );

            Product phone2 = new Product(
                "s26-ultra-512gb-black",
                "Galaxy S26 Ultra 512GB (Black)",
                "Flagship smartphone, extra storage for power users.",
                new BigDecimal("99999"),
                "INR",
                "smartphones",
                "Samsung",
                List.of("https://example.com/images/s26-ultra-512-black-1.jpg"),
                30,
                true,
                Map.of("storage", "512GB", "color", "Black")
            );

            Product earbuds = new Product(
                "buds-pro-2",
                "Galaxy Buds Pro 2",
                "Wireless earbuds with ANC and long battery life.",
                new BigDecimal("14999"),
                "INR",
                "earbuds",
                "Samsung",
                List.of("https://example.com/images/buds-pro-2-1.jpg"),
                100,
                true,
                Map.of("color", "White")
            );

            productRepository.saveAll(List.of(phone1, phone2, earbuds));
            System.out.println(">>> Seeded sample products into catalog");
        };
    }
}
