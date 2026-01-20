package com.gocommerce.cart.web;

import com.gocommerce.cart.dto.AddCartItemRequest;
import com.gocommerce.cart.entity.Cart;
import com.gocommerce.cart.entity.CartItem;
import com.gocommerce.cart.security.AuthenticatedUser;
import com.gocommerce.cart.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartControllerTest {

    // Stub CartService via subclass to avoid mocking a concrete class
    private static class RecordingCartService extends CartService {

        String lastUserIdGet;
        String lastUserIdAdd;
        String lastUserIdClear;

        Cart cartToReturn = new Cart("user-1");

        RecordingCartService() {
            super(null);
            cartToReturn.setItems(List.of(
                    new CartItem("p1", "slug-p1", "Product 1",
                            new BigDecimal("9.99"), "USD", 1, null)
            ));
        }

        @Override
        public Cart getCart(String userId) {
            this.lastUserIdGet = userId;
            return cartToReturn;
        }

        @Override
        public Cart addItem(String userId, AddCartItemRequest request) {
            this.lastUserIdAdd = userId;
            return cartToReturn;
        }

        @Override
        public void clearCart(String userId) {
            this.lastUserIdClear = userId;
        }
    }

    @Test
    void getCart_usesAuthenticatedUserIdAndReturnsCart() {
        RecordingCartService service = new RecordingCartService();
        CartController controller = new CartController(service);
        AuthenticatedUser auth = new AuthenticatedUser("user-1", "user@example.com", "User One", "USER");

        ResponseEntity<?> response = controller.getCart("user-1", auth);

        assertThat(service.lastUserIdGet).isEqualTo("user-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("data")).isSameAs(service.cartToReturn);
    }

    @Test
    void getCart_throwsUnauthorizedWhenNoPrincipal() {
        RecordingCartService service = new RecordingCartService();
        CartController controller = new CartController(service);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.getCart("user-1", null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getCart_forbiddenWhenPathUserIdDiffersFromJwt() {
        RecordingCartService service = new RecordingCartService();
        CartController controller = new CartController(service);
        AuthenticatedUser auth = new AuthenticatedUser("user-1", "user@example.com", "User One", "USER");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.getCart("other-user", auth));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void addItem_callsServiceWithAuthenticatedUser() {
        RecordingCartService service = new RecordingCartService();
        CartController controller = new CartController(service);
        AuthenticatedUser auth = new AuthenticatedUser("user-1", "user@example.com", "User One", "USER");

        AddCartItemRequest req = new AddCartItemRequest();
        req.setProductId("p1");
        req.setProductSlug("slug-p1");
        req.setName("Product 1");
        req.setPrice(new BigDecimal("9.99"));
        req.setCurrency("USD");
        req.setQuantity(1);

        ResponseEntity<?> response = controller.addItem("user-1", auth, req);

        assertThat(service.lastUserIdAdd).isEqualTo("user-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void clearCart_callsServiceWithAuthenticatedUser() {
        RecordingCartService service = new RecordingCartService();
        CartController controller = new CartController(service);
        AuthenticatedUser auth = new AuthenticatedUser("user-1", "user@example.com", "User One", "USER");

        ResponseEntity<Void> response = controller.clearCart("user-1", auth);

        assertThat(service.lastUserIdClear).isEqualTo("user-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
