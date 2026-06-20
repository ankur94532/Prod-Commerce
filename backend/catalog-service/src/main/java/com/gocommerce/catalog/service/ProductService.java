// src/main/java/com/gocommerce/catalog/service/ProductService.java
package com.gocommerce.catalog.service;

import com.gocommerce.catalog.dto.ProductFiltersResponse;
import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.MapJoin;
import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public Page<ProductResponse> listProducts(String categorySlug,
                                              String brand,
                                              BigDecimal minPrice,
                                              BigDecimal maxPrice,
                                              Boolean inStock,
                                              String color,
                                              String type,
                                              String fit,
                                              String storage,
                                              String memory,
                                              String material,
                                              Pageable pageable) {
        Specification<Product> spec = Specification.where(activeProducts())
                .and(categoryEquals(categorySlug))
                .and(brandEquals(brand))
                .and(priceGreaterThanOrEqual(minPrice))
                .and(priceLessThanOrEqual(maxPrice))
                .and(stockAvailable(inStock))
                .and(attributeEquals("color", color))
                .and(attributeEquals("type", type))
                .and(attributeEquals("fit", fit))
                .and(attributeEquals("storage", storage))
                .and(attributeEquals("memory", memory))
                .and(attributeEquals("material", material));

        return productRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new NoSuchElementException("Product not found"));
        return toResponse(product);
    }

    public ProductResponse getById(Long id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found"));
        return toResponse(product);
    }
    public List<String> getAvailableCategories() {
        return productRepository.findDistinctActiveCategorySlugs();
    }

    public ProductFiltersResponse getAvailableFilters(String categorySlug) {
        return new ProductFiltersResponse(
                getAvailableCategories(),
                productRepository.findDistinctActiveBrands(),
                new ProductFiltersResponse.PriceRange(
                        productRepository.findMinActivePrice(),
                        productRepository.findMaxActivePrice()),
                availableAttributeFilters(categorySlug));
    }

    public ProductFiltersResponse getAvailableFilters() {
        return getAvailableFilters(null);
    }

    private Specification<Product> activeProducts() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    private Specification<Product> categoryEquals(String categorySlug) {
        if (categorySlug == null || categorySlug.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("categorySlug"), categorySlug);
    }

    private Specification<Product> brandEquals(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get("brand")), brand.toLowerCase());
    }

    private Specification<Product> priceGreaterThanOrEqual(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    private Specification<Product> priceLessThanOrEqual(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private Specification<Product> stockAvailable(Boolean inStock) {
        if (!Boolean.TRUE.equals(inStock)) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0);
    }

    private Specification<Product> attributeEquals(String key, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            MapJoin<Product, String, String> attributes = root.joinMap("attributes");
            return cb.and(
                    cb.equal(attributes.key(), key),
                    cb.equal(cb.lower(attributes.value()), value.toLowerCase()));
        };
    }

    private Map<String, List<String>> availableAttributeFilters(String categorySlug) {
        Map<String, List<String>> filters = new LinkedHashMap<>();
        for (String key : List.of("color", "type", "fit", "storage", "memory", "material")) {
            List<String> values = productRepository.findDistinctActiveAttributeValues(key, categorySlug);
            if (!values.isEmpty()) {
                filters.put(key, values);
            }
        }
        return filters;
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
