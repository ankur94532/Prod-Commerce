// src/main/java/com/gocommerce/catalog/service/ProductService.java
package com.gocommerce.catalog.service;

import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<ProductResponse> listProducts(String categorySlug, Pageable pageable) {
        Page<Product> page;
        if (categorySlug != null && !categorySlug.isBlank()) {
            page = productRepository.findByCategorySlugAndActiveTrue(categorySlug, pageable);
        } else {
            page = productRepository.findByActiveTrue(pageable);
        }
        return page.map(this::toResponse);
    }

    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new NoSuchElementException("Product not found"));
        return toResponse(product);
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getCurrency(),
                p.getCategorySlug(),
                p.getBrand(),
                p.getImageUrls(),
                p.getStockQuantity(),
                p.getAttributes());
    }
}
