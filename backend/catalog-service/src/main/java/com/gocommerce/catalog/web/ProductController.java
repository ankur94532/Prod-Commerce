package com.gocommerce.catalog.web;

import com.gocommerce.catalog.dto.ProductFiltersResponse;
import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.metrics.CatalogMetrics;
import com.gocommerce.catalog.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final CatalogMetrics catalogMetrics;
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    public ProductController(ProductService productService,
                             CatalogMetrics catalogMetrics) {
        this.productService = productService;
        this.catalogMetrics = catalogMetrics;
    }

    @GetMapping
    public ResponseEntity<?> listProducts(
            @RequestParam(value = "category", required = false) String categorySlug,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "inStock", required = false) Boolean inStock,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "fit", required = false) String fit,
            @RequestParam(value = "storage", required = false) String storage,
            @RequestParam(value = "memory", required = false) String memory,
            @RequestParam(value = "material", required = false) String material,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        logger.info("Inside listProducts method: category={}, brand={}, minPrice={}, maxPrice={}, inStock={}, color={}, type={}, fit={}, storage={}, memory={}, material={}, sort={}, page={}, size={}",
                categorySlug, brand, minPrice, maxPrice, inStock, color, type, fit, storage, memory, material, sort, page, size);

        // metric: user browsed product list
        catalogMetrics.onProductList();

        Pageable pageable = PageRequest.of(page, size, sortFor(sort));
        Page<ProductResponse> result = productService.listProducts(
                categorySlug, brand, minPrice, maxPrice, inStock,
                color, type, fit, storage, memory, material, pageable);

        Map<String, Object> body = Map.of(
                "data", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages());
        logger.info("data: {}, page: {}, size: {}, totalElements: {}, totalPages: {}",
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
        return ResponseEntity.ok(body);
    }
    @GetMapping("/categories")
    public List<String> getCategories() {
        return productService.getAvailableCategories();
    }

    @GetMapping("/filters")
    public ProductFiltersResponse getFilters(
            @RequestParam(value = "category", required = false) String categorySlug) {
        return productService.getAvailableFilters(categorySlug);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getProduct(@PathVariable String slug) {
        // metric: product detail view
        catalogMetrics.onProductDetail();

        ProductResponse product = productService.getBySlug(slug);
        return ResponseEntity.ok(Map.of("data", product));
    }

    private Sort sortFor(String sort) {
        if (sort == null || sort.isBlank() || "newest".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "name_asc" -> Sort.by(Sort.Direction.ASC, "name");
            case "name_desc" -> Sort.by(Sort.Direction.DESC, "name");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }
}
