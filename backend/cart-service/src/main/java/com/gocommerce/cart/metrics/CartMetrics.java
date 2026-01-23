package com.gocommerce.cart.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CartMetrics {

    private final Counter cartViewed;
    private final Counter addItem;
    private final Counter removeItem;
    private final Counter clearCart;
    private final Counter checkoutAttempts;

    public CartMetrics(MeterRegistry registry) {
        this.cartViewed = Counter.builder("cart_view_total")
                .description("Carts viewed")
                .tag("service", "cart-service")
                .register(registry);

        this.addItem = Counter.builder("cart_add_item_total")
                .description("Items added to carts")
                .tag("service", "cart-service")
                .register(registry);

        this.removeItem = Counter.builder("cart_remove_item_total")
                .description("Items removed from carts")
                .tag("service", "cart-service")
                .register(registry);

        this.clearCart = Counter.builder("cart_clear_total")
                .description("Carts cleared")
                .tag("service", "cart-service")
                .register(registry);

        this.checkoutAttempts = Counter.builder("cart_checkout_attempts_total")
                .description("Checkout attempts from cart")
                .tag("service", "cart-service")
                .register(registry);
    }

    public void onCartViewed() {
        cartViewed.increment();
    }

    public void onItemAdded() {
        addItem.increment();
    }

    public void onCartCleared() {
        clearCart.increment();
    }

    // kept for possible future usage
    public void onAddItem() { addItem.increment(); }
    public void onRemoveItem() { removeItem.increment(); }
    public void onClearCart() { clearCart.increment(); }
    public void onCheckoutAttempt() { checkoutAttempts.increment(); }
}
