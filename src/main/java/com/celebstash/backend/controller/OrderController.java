package com.celebstash.backend.controller;

import com.celebstash.backend.dto.order.OrderRequest;
import com.celebstash.backend.dto.order.OrderResponse;
import com.celebstash.backend.dto.payment.PaymentRequest;
import com.celebstash.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order from the user's cart")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        return new ResponseEntity<>(orderService.createOrder(request), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get my orders", description = "Returns all orders for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(@PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderService.getMyOrders(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Returns an order by its ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable("id") Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    @PostMapping("/{id}/payment")
    @Operation(summary = "Process payment", description = "Processes payment for an order")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderResponse> processPayment(
            @PathVariable("id") Long orderId,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(orderService.processPayment(orderId, request.getPin()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels an order")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("id") Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }
}