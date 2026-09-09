package com.gocommerce.orders.service;

import com.gocommerce.orders.dto.OrderDtos.*;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class OrderService {
    private final OrderIntentService intents;
    private final OrderWorkflow workflow;
    private final OrderRepository orders;

    public OrderService(OrderIntentService intents, OrderWorkflow workflow, OrderRepository orders) {
        this.intents = intents; this.workflow = workflow; this.orders = orders;
    }

    public OrderResponse createOrder(CreateOrderRequest request) { return createOrder(request, null); }

    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (request == null || request.userId() == null || request.userId().isBlank()
                || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("User and nonempty items required");
        }
        Set<String> seen = new HashSet<>();
        for (var item : request.items()) {
            if (item == null || item.productId() == null || item.productId().isBlank() || item.quantity() <= 0
                    || !seen.add(item.productId())) {
                throw new IllegalArgumentException("Items require unique product IDs and positive quantities");
            }
        }
        // Legacy callers without keys remain supported, but cannot safely retry after a lost response.
        String key = idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 128) throw new IllegalArgumentException("Idempotency-Key must contain 1–128 characters");
        var intent = intents.prepare(request, key, hashRequest(request));
        // Only the creator may start payment. A replay never resumes an ambiguous payment attempt.
        return workflow.process(intent.orderId(), intent.created() ? request.payment() : null, intent.created());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrdersForUser(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(OrderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findAttempt(String userId, String key) {
        return orders.findByUserIdAndIdempotencyKey(userId, key.trim()).map(OrderService::toResponse)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    static String legacyHashRequest(CreateOrderRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.userId() + "|").getBytes(StandardCharsets.UTF_8));
            request.items().stream().sorted(Comparator.comparing(CreateOrderItemRequest::productId)).forEach(item ->
                    digest.update((item.productId() + ":" + item.quantity() + "|").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static String hashRequest(CreateOrderRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Length-prefix strings; payment credentials are deliberately never persisted or hashed.
            request.items().stream().sorted(Comparator.comparing(CreateOrderItemRequest::productId)).forEach(item -> {
                byte[] id = item.productId().getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(id.length).array());
                digest.update(id);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(item.quantity()).array());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(),
                        i.getProductId(),
                        i.getProductName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                itemResponses
        );
    }

}
