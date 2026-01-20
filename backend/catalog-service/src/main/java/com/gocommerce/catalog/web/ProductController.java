package com.gocommerce.catalog.web;

import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<?> listProducts(
            @RequestParam(value = "category", required = false) String categorySlug,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        logger.info("Inside listProducts method: category={}, page={}, size={}",
                categorySlug, page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductResponse> result = productService.listProducts(categorySlug, pageable);

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

    @GetMapping("/{slug}")
    public ResponseEntity<?> getProduct(@PathVariable String slug) {
        ProductResponse product = productService.getBySlug(slug);
        return ResponseEntity.ok(Map.of("data", product));
    }
}
