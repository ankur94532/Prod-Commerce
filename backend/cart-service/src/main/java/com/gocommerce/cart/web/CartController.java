package com.gocommerce.cart.web;

import com.gocommerce.cart.dto.AddCartItemRequest;
import com.gocommerce.cart.entity.Cart;
import com.gocommerce.cart.entity.CartItem;
import com.gocommerce.cart.security.AuthenticatedUser;
import com.gocommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    private String validateAndResolveUserId(String pathUserId, AuthenticatedUser authUser) {
        if (authUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        String jwtUserId = authUser.id();

        // If client passes a path userId, it MUST match JWT subject
        if (pathUserId != null && !pathUserId.isBlank() && !pathUserId.equals(jwtUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's cart");
        }

        return jwtUserId;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getCart(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        String effectiveUserId = validateAndResolveUserId(userId, authUser);

        Cart cart = cartService.getCart(effectiveUserId);
        logger.info("Get cart for userId={}", effectiveUserId);
        for (CartItem item : cart.getItems()) {
            logger.info("Cart Item: Name={}, Quantity={}", item.getName(), item.getQuantity());
        }
        return ResponseEntity.ok(Map.of("data", cart));
    }

    @PostMapping("/{userId}/items")
    public ResponseEntity<?> addItem(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthenticatedUser authUser,
            @Valid @RequestBody AddCartItemRequest request) {

        String effectiveUserId = validateAndResolveUserId(userId, authUser);

        Cart cart = cartService.addItem(effectiveUserId, request);
        for (CartItem item : cart.getItems()) {
            logger.info("Cart Item: Name={}, Quantity={}", item.getName(), item.getQuantity());
        }
        return ResponseEntity.ok(Map.of("data", cart));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearCart(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        String effectiveUserId = validateAndResolveUserId(userId, authUser);

        cartService.clearCart(effectiveUserId);
        logger.info("Cleared cart for userId={}", effectiveUserId);
        return ResponseEntity.noContent().build();
    }
}
