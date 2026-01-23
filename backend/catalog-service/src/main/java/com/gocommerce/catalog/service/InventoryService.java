package com.gocommerce.catalog.service;

import com.gocommerce.catalog.exception.OutOfStockException;
import com.gocommerce.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Atomically decrements stock using a single UPDATE statement.
     * Throws OutOfStockException if there is not enough stock.
     */
    @Transactional
    public void decrementStock(Long productId, int quantity) {
        int updated = productRepository.decrementStockIfEnough(productId, quantity);
        if (updated == 0) {
            throw new OutOfStockException(productId, quantity);
        }
    }
}
