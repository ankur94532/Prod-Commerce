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

        Cart result = cartService.addItem("user-1", req, "add-1");

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getItems()).hasSize(1);
        CartItem item = result.getItems().get(0);
        assertThat(item.getProductId()).isEqualTo("p1");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(result.getRevision()).isEqualTo(1);

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

        Cart result = cartService.addItem("user-1", req, "add-2");

        assertThat(result.getItems()).hasSize(1);
        CartItem item = result.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(result.getRevision()).isEqualTo(1);

        verify(cartRepository).save(existingCart);
    }

    @Test
    void clearCart_deletesOnlyAtExpectedRevision() {
        Cart existing = new Cart("user-1");
        existing.setRevision(3);
        when(cartRepository.findById("user-1")).thenReturn(Optional.of(existing));

        cartService.clearCart("user-1", 3);

        verify(cartRepository).deleteById("user-1");
    }

    @Test
    void staleClearCannotDeleteAConcurrentlyChangedCart() {
        Cart changed = new Cart("user-1");
        changed.setRevision(4);
        when(cartRepository.findById("user-1")).thenReturn(Optional.of(changed));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cartService.clearCart("user-1", 3))
                .isInstanceOf(CartConflictException.class)
                .hasMessageContaining("expected revision 3").hasMessageContaining("found 4");
        verify(cartRepository, never()).deleteById(anyString());
    }

    @Test
    void repeatedAddWithSameKeyDoesNotIncrementTwice() {
        Cart existing = new Cart("user-1");
        when(cartRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(cartRepository.save(existing)).thenReturn(existing);
        AddCartItemRequest request = buildRequest("p1", 2);

        Cart first = cartService.addItem("user-1", request, "stable-key");
        Cart replay = cartService.addItem("user-1", request, "stable-key");

        assertThat(first.getRevision()).isEqualTo(1);
        assertThat(replay.getItems().get(0).getQuantity()).isEqualTo(2);
        verify(cartRepository, times(1)).save(existing);
    }

    @Test
    void reusedKeyWithDifferentPayloadFailsClosed() {
        Cart existing = new Cart("user-1");
        when(cartRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(cartRepository.save(existing)).thenReturn(existing);
        cartService.addItem("user-1", buildRequest("p1", 1), "stable-key");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> cartService.addItem("user-1", buildRequest("p1", 2), "stable-key"))
                .isInstanceOf(CartConflictException.class)
                .hasMessageContaining("another cart mutation");
    }

    @Test
    void clearingAnAlreadyEmptyRevisionZeroCartIsIdempotent() {
        when(cartRepository.findById("user-1")).thenReturn(Optional.empty());

        cartService.clearCart("user-1", 0);

        verify(cartRepository, never()).deleteById(anyString());
    }
}
