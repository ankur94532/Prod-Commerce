package com.gocommerce.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDtos {

    public record CreateOrderItemRequest(
            @NotBlank String productId,
            @NotBlank String productName,
            @Min(1) int quantity,
            @NotNull BigDecimal unitPrice
    ) {}

    public record CreateOrderRequest(
            @NotBlank String userId,
            @NotNull List<CreateOrderItemRequest> items,
            PaymentDetails payment
    ) {}

    public record PaymentDetails(
            String cardNumber,
            String cardExpiry,
            String cardCvc
    ) {}

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
