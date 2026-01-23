package com.gocommerce.catalog.web;

import com.gocommerce.catalog.dto.AdminProductRequest;
import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.metrics.CatalogMetrics;
import com.gocommerce.catalog.service.AdminProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private static final Logger logger = LoggerFactory.getLogger(AdminProductController.class);

    private final AdminProductService adminProductService;
    private final CatalogMetrics catalogMetrics;
    @Autowired
    public AdminProductController(AdminProductService adminProductService,
                                  CatalogMetrics catalogMetrics) {
        this.adminProductService = adminProductService;
        this.catalogMetrics = catalogMetrics;
    }

    // ctor kept for tests that only pass the service
    public AdminProductController(AdminProductService adminProductService) {
        this(adminProductService, null);
    }

    // ---------- LIST ----------

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Product> result = adminProductService.listProducts(pageable);

        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "items", items,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );

        logger.info("Admin product list page={} size={} total={}", page, size, result.getTotalElements());

        if (catalogMetrics != null) {
            catalogMetrics.onProductList();
        }

        return ResponseEntity.ok(body);
    }

    // ---------- GET ONE ----------

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        Product product = adminProductService.getProduct(id);

        if (catalogMetrics != null) {
            catalogMetrics.onProductDetail();
        }

        return ResponseEntity.ok(Map.of("item", toSummary(product)));
    }

    // ---------- CREATE ----------

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody AdminProductRequest request) {
        Product created = adminProductService.createProduct(request);
        logger.info("Created product id={} slug={}", created.getId(), created.getSlug());

        if (catalogMetrics != null) {
            catalogMetrics.onProductCreated();
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("item", toSummary(created)));
    }

    // ---------- UPDATE (full) ----------

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminProductRequest request
    ) {
        Product updated = adminProductService.updateProduct(id, request);
        logger.info(
                "Updated product id={} slug={} name={} price={} currency={} categorySlug={} brand={} stockQuantity={} active={}",
                updated.getId(),
                updated.getSlug(),
                updated.getName(),
                updated.getPrice(),
                updated.getCurrency(),
                updated.getCategorySlug(),
                updated.getBrand(),
                updated.getStockQuantity(),
                updated.isActive()
        );

        if (catalogMetrics != null) {
            catalogMetrics.onProductUpdated();
        }

        return ResponseEntity.ok(Map.of("item", toSummary(updated)));
    }

    // ---------- UPDATE STATUS (active flag only) ----------

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Object activeRaw = body.get("active");
        if (activeRaw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'active' field is required");
        }

        boolean active;
        if (activeRaw instanceof Boolean b) {
            active = b;
        } else {
            active = Boolean.parseBoolean(activeRaw.toString());
        }

        Product updated = adminProductService.updateStatus(id, active);
        logger.info("Updated product status id={} active={}", id, active);

        if (catalogMetrics != null) {
            catalogMetrics.onProductUpdated();
        }

        return ResponseEntity.ok(Map.of(
                "id", updated.getId(),
                "active", updated.isActive()
        ));
    }

    // ---------- DELETE (hard delete) ----------

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        adminProductService.deleteProduct(id);
        logger.info("Deleted product id={}", id);

        if (catalogMetrics != null) {
            catalogMetrics.onProductDeleted();
        }

        return ResponseEntity.noContent().build();
    }

    // ---------- Helpers ----------

    private Map<String, Object> toSummary(Product p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("slug", p.getSlug());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("price", p.getPrice());
        map.put("currency", p.getCurrency());
        map.put("categorySlug", p.getCategorySlug());
        map.put("brand", p.getBrand());
        map.put("stockQuantity", p.getStockQuantity());
        map.put("active", p.isActive());
        map.put("imageUrls", p.getImageUrls());
        return map;
    }
}
