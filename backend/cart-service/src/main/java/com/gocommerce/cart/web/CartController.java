package com.gocommerce.cart.web;

import com.gocommerce.cart.dto.AddCartItemRequest;
import com.gocommerce.cart.entity.Cart;
import com.gocommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // TEMP: userId as query param.
    // Later we'll get userId from JWT in a filter.
    @GetMapping
    public ResponseEntity<?> getCart(@RequestParam("userId") String userId) {
        Cart cart = cartService.getCart(userId);
        return ResponseEntity.ok(Map.of("data", cart));
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(
        @RequestParam("userId") String userId,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        Cart cart = cartService.addItem(userId, request);
        return ResponseEntity.ok(Map.of("data", cart));
    }
}
