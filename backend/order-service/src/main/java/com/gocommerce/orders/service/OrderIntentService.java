package com.gocommerce.orders.service;

import com.gocommerce.orders.client.CatalogClient;
import com.gocommerce.orders.client.CatalogClient.ProductSnapshot;
import com.gocommerce.orders.dto.OrderDtos.*;
import com.gocommerce.orders.exception.IdempotencyConflictException;
import com.gocommerce.orders.metrics.OrderMetrics;
import com.gocommerce.orders.model.*;
import com.gocommerce.orders.repository.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderIntentService {
    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final OrderMetrics orderMetrics;
    private final JdbcTemplate jdbc;
    public OrderIntentService(OrderRepository repository, CatalogClient catalog, OrderMetrics metrics, JdbcTemplate jdbc) {
        this.orderRepository = repository; this.catalogClient = catalog; this.orderMetrics = metrics; this.jdbc = jdbc;
    }

    public record Intent(Long orderId, boolean created) {}

    @Transactional
    public Intent prepare(CreateOrderRequest request, String normalizedIdempotencyKey, String requestHash) {
        // Database-wide lock, including first insertion; hash collisions only serialize unrelated requests.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))", request.userId(), normalizedIdempotencyKey);
        var existing = orderRepository.findByUserIdAndIdempotencyKey(request.userId(), normalizedIdempotencyKey);
        if (existing.isPresent()) {
            Order order = existing.get();
            String expectedHash = order.getWorkflowVersion() == 0 ? OrderService.legacyHashRequest(request) : requestHash;
            if (!expectedHash.equals(order.getIdempotencyRequestHash())) {
                throw new IdempotencyConflictException("Idempotency-Key was already used with a different order payload");
            }
            return new Intent(order.getId(), false);
        }
        return new Intent(create(request, normalizedIdempotencyKey, requestHash).getId(), true);
    }

    private Order create(CreateOrderRequest request, String normalizedIdempotencyKey, String requestHash) {
        List<PricedOrderLine> pricedLines = priceFromCatalog(request.items());
        String currency = validateAndResolveCurrency(pricedLines);
        BigDecimal total = pricedLines.stream()
                .map(PricedOrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        orderMetrics.onOrderCreated();
                        orderMetrics.recordOrderValue(total);
                    }
                });

        Order order = new Order(
                request.userId(),
                OrderStatus.PENDING_PAYMENT,
                total,
                currency,
                normalizedIdempotencyKey,
                requestHash
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

        return orderRepository.saveAndFlush(order);

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

    private record PricedOrderLine(ProductSnapshot product, int quantity) {
        BigDecimal lineTotal() { return product.unitPrice().multiply(BigDecimal.valueOf(quantity)); }
    }
}
