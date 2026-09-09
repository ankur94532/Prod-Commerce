package com.gocommerce.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDtos {

    /**
     * productName/unitPrice are kept only for backward-compatible clients.
     * Order-service ignores those values and snapshots name/price from catalog-service.
     */
    public record CreateOrderItemRequest(
            @NotBlank String productId,
            String productName,
            @Min(1) int quantity,
            BigDecimal unitPrice
    ) {}

    public record CreateOrderRequest(
            String userId,
            @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items,
            @Valid PaymentDetails payment
    ) {}

    /**
     * A processor token, never card data.
     *
     * <p>This API used to accept the card number, expiry, and CVC. That put every service
     * on the request path, every log, every heap dump, and every database backup inside
     * PCI DSS scope. The browser now sends the card straight to the payment processor and
     * hands us only the token it returns, so the card never reaches this system at all.
     *
     * <p>The pattern is what enforces that: a card number cannot satisfy it, so a client
     * that tries to send one is rejected before any handler sees the value. The message
     * deliberately does not echo the rejected input.
     */
    public record PaymentDetails(
            @Pattern(regexp = "^pm_[A-Za-z0-9_]{6,64}$",
                    message = "must be a payment processor token; card details must never be sent to this API")
            String paymentToken
    ) {
        @Override public String toString() { return "PaymentDetails[token]"; }
    }

    public record OrderItemResponse(
            Long id,
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    public record OrderResponse(
            Long id,
            String userId,
            String status,
            BigDecimal totalAmount,
            Instant createdAt,
            List<OrderItemResponse> items
    ) {}
}
