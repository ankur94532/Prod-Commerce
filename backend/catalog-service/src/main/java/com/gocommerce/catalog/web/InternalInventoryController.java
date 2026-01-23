package com.gocommerce.catalog.web;

import com.gocommerce.catalog.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/inventory/products")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    public InternalInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Internal endpoint used by order-service to decrement stock.
     * Example: POST /api/v1/internal/inventory/products/42/decrement?quantity=2
     */
    @PostMapping("/{productId}/decrement")
    public ResponseEntity<Void> decrementStock(@PathVariable Long productId,
                                               @RequestParam(name = "quantity", defaultValue = "1") int quantity) {
        inventoryService.decrementStock(productId, quantity);
        return ResponseEntity.noContent().build();
    }
}
