package com.gocommerce.cart.service;

import com.gocommerce.cart.dto.AddCartItemRequest;
import com.gocommerce.cart.entity.Cart;
import com.gocommerce.cart.entity.CartItem;
import com.gocommerce.cart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Optional;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartMutationCoordinator mutationCoordinator;

    @Autowired
    public CartService(CartRepository cartRepository, CartMutationCoordinator mutationCoordinator) {
        this.cartRepository = cartRepository;
        this.mutationCoordinator = mutationCoordinator;
    }

    /** Test seam for pure service tests; production always injects the Redis coordinator. */
    public CartService(CartRepository cartRepository) {
        this(cartRepository, null);
    }

    public Cart getCart(String userId) {
        return cartRepository.findById(userId)
                .orElseGet(() -> new Cart(userId));
    }

    public Cart addItem(String userId, AddCartItemRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        return coordinated(userId, () -> addItemLocked(userId, request, idempotencyKey));
    }

    private Cart addItemLocked(String userId, AddCartItemRequest request, String idempotencyKey) {
        Cart cart = cartRepository.findById(userId)
                .orElseGet(() -> new Cart(userId));
        String fingerprint = fingerprint(request);
        String existingFingerprint = cart.getAppliedMutations().get(idempotencyKey);
        if (existingFingerprint != null) {
            if (!existingFingerprint.equals(fingerprint)) {
                throw new CartConflictException("Idempotency key was already used for another cart mutation");
            }
            return cart;
        }

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

        cart.setRevision(cart.getRevision() + 1);
        rememberMutation(cart, idempotencyKey, fingerprint);
        return cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(String userId, long expectedRevision) {
        coordinated(userId, () -> {
            Optional<Cart> cart = cartRepository.findById(userId);
            long actualRevision = cart.map(Cart::getRevision).orElse(0L);
            if (actualRevision != expectedRevision) {
                throw new CartConflictException(
                        "Cart changed: expected revision " + expectedRevision + " but found " + actualRevision);
            }
            cart.ifPresent(existing -> cartRepository.deleteById(userId));
            return null;
        });
    }

    private <T> T coordinated(String userId, java.util.function.Supplier<T> operation) {
        return mutationCoordinator == null ? operation.get() : mutationCoordinator.execute(userId, operation);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new InvalidCartMutationException("Idempotency-Key must contain 1 to 128 characters");
        }
    }

    private static String fingerprint(AddCartItemRequest request) {
        String canonical = String.join("\u001f", request.getProductId(), request.getProductSlug(), request.getName(),
                request.getPrice().stripTrailingZeros().toPlainString(), request.getCurrency(),
                Integer.toString(request.getQuantity()), Optional.ofNullable(request.getImageUrl()).orElse(""));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void rememberMutation(Cart cart, String key, String fingerprint) {
        LinkedHashMap<String, String> mutations = new LinkedHashMap<>(cart.getAppliedMutations());
        mutations.put(key, fingerprint);
        while (mutations.size() > 100) {
            mutations.remove(mutations.keySet().iterator().next());
        }
        cart.setAppliedMutations(mutations);
    }
}
