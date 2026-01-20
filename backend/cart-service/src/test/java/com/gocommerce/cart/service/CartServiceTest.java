package com.gocommerce.cart.service;

import com.gocommerce.cart.dto.AddCartItemRequest;
import com.gocommerce.cart.entity.Cart;
import com.gocommerce.cart.entity.CartItem;
import com.gocommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository);
    }

    @Test
    void getCart_returnsExistingCartWhenPresent() {
        Cart existing = new Cart("user-1");
        existing.getItems().add(new CartItem(
                "p1", "slug-p1", "Product 1",
                new BigDecimal("10.00"), "USD", 1, null));
        when(cartRepository.findById("user-1")).thenReturn(Optional.of(existing));

        Cart result = cartService.getCart("user-1");

        assertThat(result).isSameAs(existing);
        verify(cartRepository).findById("user-1");
    }

    @Test
    void getCart_returnsNewEmptyCartWhenMissing() {
        when(cartRepository.findById("user-1")).thenReturn(Optional.empty());

        Cart result = cartService.getCart("user-1");

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getItems()).isEmpty();
        verify(cartRepository).findById("user-1");
    }

    private AddCartItemRequest buildRequest(String productId, int quantity) {
        AddCartItemRequest req = new AddCartItemRequest();
        req.setProductId(productId);
        req.setProductSlug("slug-" + productId);
        req.setName("Product " + productId);
        req.setPrice(new BigDecimal("9.99"));
        req.setCurrency("USD");
        req.setQuantity(quantity);
        req.setImageUrl("http://example.com/img.png");
        return req;
    }

    @Test
    void addItem_createsNewItemWhenNotPresent() {
        when(cartRepository.findById("user-1")).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        AddCartItemRequest req = buildRequest("p1", 2);

        Cart result = cartService.addItem("user-1", req);

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getItems()).hasSize(1);
        CartItem item = result.getItems().get(0);
        assertThat(item.getProductId()).isEqualTo("p1");
        assertThat(item.getQuantity()).isEqualTo(2);

        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addItem_incrementsQuantityWhenItemAlreadyPresent() {
        Cart existingCart = new Cart("user-1");
        existingCart.getItems().add(
                new CartItem("p1", "slug-p1", "Product 1",
                        new BigDecimal("9.99"), "USD", 3, null)
        );
        when(cartRepository.findById("user-1")).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        AddCartItemRequest req = buildRequest("p1", 2);

        Cart result = cartService.addItem("user-1", req);

        assertThat(result.getItems()).hasSize(1);
        CartItem item = result.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(5);

        verify(cartRepository).save(existingCart);
    }

    @Test
    void clearCart_deletesById() {
        cartService.clearCart("user-1");

        verify(cartRepository).deleteById("user-1");
    }
}
