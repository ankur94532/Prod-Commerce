package com.gocommerce.catalog.web;

import com.gocommerce.catalog.exception.OutOfStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<Map<String, Object>> handleOutOfStock(OutOfStockException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "OUT_OF_STOCK");
        body.put("message", ex.getMessage());
        body.put("productId", ex.getProductId());
        body.put("requested", ex.getRequested());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
