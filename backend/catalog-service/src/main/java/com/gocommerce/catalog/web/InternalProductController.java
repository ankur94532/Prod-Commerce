package com.gocommerce.catalog.web;

import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/products")
public class InternalProductController {

    private final ProductService productService;

    public InternalProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductSnapshot(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getById(productId));
    }
}
