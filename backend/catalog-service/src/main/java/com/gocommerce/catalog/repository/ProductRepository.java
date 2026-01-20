// src/main/java/com/gocommerce/catalog/repository/ProductRepository.java
package com.gocommerce.catalog.repository;

import com.gocommerce.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategorySlugAndActiveTrue(String categorySlug, Pageable pageable);

    Optional<Product> findBySlugAndActiveTrue(String slug);
}
