package com.gocommerce.cart.service;

import com.gocommerce.cart.dto.AddCartItemRequest;
import com.gocommerce.cart.entity.Cart;
import com.gocommerce.cart.entity.CartItem;
import com.gocommerce.cart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public Cart getCart(String userId) {
        return cartRepository.findById(userId)
                .orElseGet(() -> new Cart(userId));
    }

    public Cart addItem(String userId, AddCartItemRequest request) {
        Cart cart = cartRepository.findById(userId)
                .orElseGet(() -> new Cart(userId));

        // if item with same productId exists, increase quantity
        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
        } else {
            CartItem newItem = new CartItem(
                    request.getProductId(),
                    request.getProductSlug(),
                    request.getName(),
                    request.getPrice(),
                    request.getCurrency(),
                    request.getQuantity(),
                    request.getImageUrl());
            cart.getItems().add(newItem);
        }

        return cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(String userId) {
        cartRepository.deleteById(userId);
    }
}
