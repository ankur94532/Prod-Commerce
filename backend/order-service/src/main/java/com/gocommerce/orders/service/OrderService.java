package com.gocommerce.orders.service;

import com.gocommerce.orders.client.CatalogClient;
import com.gocommerce.orders.client.CatalogClient.ProductSnapshot;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderItemRequest;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderRequest;
import com.gocommerce.orders.dto.OrderDtos.OrderItemResponse;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.exception.PaymentFailedException;
import com.gocommerce.orders.metrics.OrderMetrics;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.model.OrderItem;
import com.gocommerce.orders.model.OrderStatus;
import com.gocommerce.orders.outbox.OrderOutboxService;
import com.gocommerce.orders.payment.PaymentChargeRequest;
import com.gocommerce.orders.payment.PaymentProvider;
import com.gocommerce.orders.payment.PaymentResult;
import com.gocommerce.orders.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderOutboxService orderOutboxService;
    private final OrderMetrics orderMetrics;
    private final PaymentProvider paymentProvider;
    private final CatalogClient catalogClient;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderOutboxService orderOutboxService,
                        OrderMetrics orderMetrics,
                        PaymentProvider paymentProvider,
                        CatalogClient catalogClient) {
        this.orderRepository = orderRepository;
        this.orderOutboxService = orderOutboxService;
        this.orderMetrics = orderMetrics;
        this.paymentProvider = paymentProvider;
        this.catalogClient = catalogClient;
    }

    /**
     * Kept for older unit tests. New production code should use the full constructor.
     */
    public OrderService(OrderRepository orderRepository,
                        OrderOutboxService orderOutboxService,
                        CatalogClient catalogClient) {
        this(orderRepository, orderOutboxService, null, req -> PaymentResult.success("test", "test-tx"), catalogClient);
    }

    @Transactional(noRollbackFor = PaymentFailedException.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        return createOrder(request, null);
    }

    @Transactional(noRollbackFor = PaymentFailedException.class)
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            var existing = orderRepository.findByUserIdAndIdempotencyKey(request.userId(), normalizedIdempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        List<PricedOrderLine> pricedLines = priceFromCatalog(request.items());
        String currency = validateAndResolveCurrency(pricedLines);
        BigDecimal total = pricedLines.stream()
                .map(PricedOrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (orderMetrics != null) {
            orderMetrics.onOrderCreated();
            orderMetrics.recordOrderValue(total);
        }

        Order order = new Order(
                request.userId(),
                OrderStatus.PENDING_PAYMENT,
                total,
                currency,
                normalizedIdempotencyKey
        );

        for (PricedOrderLine line : pricedLines) {
            ProductSnapshot product = line.product();
            order.addItem(new OrderItem(
                    product.productId(),
                    product.productName(),
                    line.quantity(),
                    product.unitPrice()
            ));
        }

        Order saved = orderRepository.save(order);

        List<PricedOrderLine> decremented = new ArrayList<>();
        try {
            for (PricedOrderLine line : pricedLines) {
                catalogClient.decrementStock(line.product().productId(), line.quantity());
                decremented.add(line);
            }
        } catch (RuntimeException e) {
            compensateStock(decremented);
            throw e;
        }

        if (paymentProvider != null) {
            String cardNumber = request.payment() != null ? request.payment().cardNumber() : null;

            PaymentChargeRequest chargeRequest = new PaymentChargeRequest(
                    total,
                    currency,
                    cardNumber,
                    "Order " + saved.getId() + " for user " + request.userId()
            );

            PaymentResult result = paymentProvider.charge(chargeRequest);
            if (!result.success()) {
                compensateStock(decremented);
                saved.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(saved);
                throw new PaymentFailedException("Payment failed: " + result.failureReason());
            }
            saved.setPaymentProvider(result.provider());
            saved.setPaymentTransactionId(result.transactionId());
        }

        saved.setStatus(OrderStatus.PAID);
        saved = orderRepository.save(saved);
        orderOutboxService.enqueueOrderCreated(saved);

        if (orderMetrics != null) {
            orderMetrics.onOrderCompleted();
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrdersForUser(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private List<PricedOrderLine> priceFromCatalog(List<CreateOrderItemRequest> requestItems) {
        return requestItems.stream()
                .map(item -> {
                    ProductSnapshot product = catalogClient.getProductSnapshot(item.productId());
                    if (product.unitPrice() == null) {
                        throw new IllegalStateException("Catalog returned null price for product " + item.productId());
                    }
                    return new PricedOrderLine(product, item.quantity());
                })
                .toList();
    }

    private String validateAndResolveCurrency(List<PricedOrderLine> pricedLines) {
        String currency = pricedLines.get(0).product().currency() != null
                ? pricedLines.get(0).product().currency()
                : "INR";
        boolean mixedCurrency = pricedLines.stream()
                .map(line -> line.product().currency() != null ? line.product().currency() : "INR")
                .anyMatch(lineCurrency -> !lineCurrency.equals(currency));
        if (mixedCurrency) {
            throw new IllegalStateException("Mixed-currency orders are not supported");
        }
        return currency;
    }

    private void compensateStock(List<PricedOrderLine> decremented) {
        for (PricedOrderLine line : decremented) {
            try {
                catalogClient.incrementStock(line.product().productId(), line.quantity());
            } catch (RuntimeException ignored) {
                // In production this should emit an alert and compensating-retry task.
            }
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 128 characters");
        }
        return trimmed;
    }

    private OrderResponse toResponse(Order order) {
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

    private record PricedOrderLine(ProductSnapshot product, int quantity) {
        BigDecimal lineTotal() {
            return product.unitPrice().multiply(BigDecimal.valueOf(quantity));
        }
    }
}
