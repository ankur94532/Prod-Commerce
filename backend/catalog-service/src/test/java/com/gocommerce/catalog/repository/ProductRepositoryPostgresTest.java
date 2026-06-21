package com.gocommerce.catalog.repository;

import com.gocommerce.catalog.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryPostgresTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductRepository productRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void findBySlugAndActiveTrue_usesRealPostgres() {
        Product product = new Product(
                "test-running-shoe",
                "Test Running Shoe",
                "Lightweight running shoe",
                new BigDecimal("2499.00"),
                "INR",
                "footwear",
                "Nike",
                List.of("https://example.com/shoe.jpg"),
                12,
                true,
                Map.of("color", "Black", "type", "Running Shoe"));

        productRepository.saveAndFlush(product);

        assertThat(productRepository.findBySlugAndActiveTrue("test-running-shoe"))
                .isPresent()
                .get()
                .extracting(Product::getCategorySlug)
                .isEqualTo("footwear");
    }
}
