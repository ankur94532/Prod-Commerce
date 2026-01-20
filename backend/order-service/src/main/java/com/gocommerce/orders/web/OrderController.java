package com.gocommerce.orders.web;

import com.gocommerce.orders.dto.OrderDtos.CreateOrderRequest;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.security.AuthenticatedUser;
import com.gocommerce.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/health")
    public String health() {
        return "order-service:OK";
    }

    /**
     * Resolve the authenticated user's ID from the SecurityContext.
     * Works both with our JWT principal (AuthenticatedUser)
     * and with @WithMockUser in tests (UserDetails / String).
     */
    private String resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        Object principal = auth.getPrincipal();

        // Normal runtime: principal set by JwtAuthenticationFilter
        if (principal instanceof AuthenticatedUser au) {
            return au.id();
        }

        // Tests with @WithMockUser, or other UserDetails
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        // Sometimes principal is just a username String
        if (principal instanceof String s) {
            return s;
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported principal type");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@RequestBody @Valid CreateOrderRequest request) {
        String authUserId = resolveAuthenticatedUserId();

        // ❌ Block attempts to create orders for a different user
        if (request.userId() != null
                && !request.userId().isBlank()
                && !request.userId().equals(authUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create order for another user");
        }

        // ✅ Always use ID from JWT / Authentication, never trust the body
        CreateOrderRequest secureRequest = new CreateOrderRequest(authUserId, request.items());

        return orderService.createOrder(secureRequest);
    }

    @GetMapping
    public List<OrderResponse> getUserOrders(@RequestParam("userId") String userId) {
        String authUserId = resolveAuthenticatedUserId();

        // ❌ Block reading someone else's orders
        if (!userId.equals(authUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another user's orders");
        }

        // ✅ Only ever list orders for the authenticated user
        return orderService.listOrdersForUser(authUserId);
    }
}
