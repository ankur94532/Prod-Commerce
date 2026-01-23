package com.gocommerce.catalog.exception;

public class OutOfStockException extends RuntimeException {

    private final Long productId;
    private final int requested;

    public OutOfStockException(Long productId, int requested) {
        super("Not enough stock for product " + productId + " (requested " + requested + ")");
        this.productId = productId;
        this.requested = requested;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRequested() {
        return requested;
    }
}
