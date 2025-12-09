package com.gocommerce.catalog.repository;

import com.gocommerce.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategorySlugAndActiveTrue(String categorySlug, Pageable pageable);

    Optional<Product> findBySlugAndActiveTrue(String slug);
}
