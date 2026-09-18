package com.celebstash.backend.service;

import com.celebstash.backend.dto.order.DeliveryOptionRequest;
import com.celebstash.backend.dto.order.OrderItemResponse;
import com.celebstash.backend.dto.order.OrderRequest;
import com.celebstash.backend.dto.order.OrderResponse;
import com.celebstash.backend.dto.payment.PaymentRequest;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.DeliveryOption;
import com.celebstash.backend.model.enums.OrderStatus;
import com.celebstash.backend.model.enums.ReservationStatus;
import com.celebstash.backend.model.enums.TransactionStatus;
import com.celebstash.backend.model.enums.TransactionType;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final LocationRepository locationRepository;
    private final CartService cartService;
    private final UserService userService;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final ReservationRepository reservationRepository;
    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    /**
     * Create a new order from the user's cart
     * @param request the order request
     * @return the created order response
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User currentUser = userService.getCurrentUser();
        
        // Get the user's cart
        Cart cart = cartService.getOrCreateCart();
        
        // Check if cart is empty
        if (cart.getItems().isEmpty()) {
            throw new AppException("Cart is empty", HttpStatus.BAD_REQUEST);
        }
        
        // Create the order
        Order order = Order.builder()
                .user(currentUser)
                .status(OrderStatus.PENDING_PAYMENT)
                .deliveryOption(request.getDeliveryOption())
                .createdAt(LocalDateTime.now())
                .build();
        
        // Set location if delivery option is DELIVERY
        if (request.getDeliveryOption() == DeliveryOption.DELIVERY) {
            if (request.getLocationId() == null) {
                throw new AppException("Location is required for delivery", HttpStatus.BAD_REQUEST);
            }
            
            Location location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new AppException("Location not found", HttpStatus.NOT_FOUND));
            
            order.setLocation(location);
            order.setDeliveryFee(location.getDeliveryFee());
        } else {
            order.setDeliveryFee(BigDecimal.ZERO);
        }
        
        // Set notes if provided
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }
        
        // Save the order to generate ID
        Order savedOrder = orderRepository.save(order);
        
        // Create order items from cart items
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            
            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .build();
            
            orderItems.add(orderItem);
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }
        
        // Save order items
        orderItemRepository.saveAll(orderItems);
        
        // Update order with totals
        savedOrder.setSubtotal(subtotal);
        savedOrder.setTotal(subtotal.add(savedOrder.getDeliveryFee()));
        savedOrder = orderRepository.save(savedOrder);
        
        // Process payment if requested
        if (request.isPayNow()) {
            if (request.getPin() == null) {
                throw new AppException("PIN is required for payment", HttpStatus.BAD_REQUEST);
            }
            
            // Process payment
            processPayment(savedOrder.getId(), request.getPin());
        }
        
        // Clear the cart after creating the order
        cartService.clearCart();
        
        return mapToOrderResponse(savedOrder);
    }

    /**
     * Process payment for an order
     * @param orderId the order ID
     * @param pin the wallet PIN
     * @return the updated order response
     */
    @Transactional
    public OrderResponse processPayment(Long orderId, String pin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        
        // Check if order is already paid
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppException("Order is already paid or cancelled", HttpStatus.BAD_REQUEST);
        }

        User currentUser = order.getUser();
        Wallet wallet = walletRepository.findByUser(currentUser)
                .orElseThrow(() -> new AppException("Wallet not found", HttpStatus.NOT_FOUND));

        if (wallet.getPin() == null || !wallet.getPin().equals(pin)) {
            throw new AppException("Invalid PIN", HttpStatus.BAD_REQUEST);
        }

        // Find active reservations for the products in the order
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        BigDecimal totalHeldEscrow = BigDecimal.ZERO;
        List<Reservation> activeReservations = new ArrayList<>();

        for (OrderItem item : orderItems) {
            Optional<Reservation> resOpt = reservationRepository.findByUserAndProductAndStatus(
                    currentUser, item.getProduct(), ReservationStatus.PENDING
            );
            if (resOpt.isPresent()) {
                Reservation res = resOpt.get();
                totalHeldEscrow = totalHeldEscrow.add(res.getHeldAmount());
                activeReservations.add(res);
            }
        }

        BigDecimal remainingToPay = order.getTotal().subtract(totalHeldEscrow);
        if (remainingToPay.compareTo(BigDecimal.ZERO) < 0) {
            remainingToPay = BigDecimal.ZERO;
        }

        if (wallet.getBalance().compareTo(remainingToPay) < 0) {
            throw new AppException("Insufficient balance. Remaining amount to pay is $" + remainingToPay.setScale(2, java.math.RoundingMode.HALF_UP), HttpStatus.BAD_REQUEST);
        }

        // Deduct remaining and release escrow
        wallet.setBalance(wallet.getBalance().subtract(remainingToPay));
        wallet.setHeldBalance(wallet.getHeldBalance().subtract(totalHeldEscrow));
        walletRepository.save(wallet);

        // Record payment transaction for buyer
        Transaction paymentTx = Transaction.builder()
                .wallet(wallet)
                .amount(order.getTotal())
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .description("Payment for Order " + order.getOrderNumber())
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(paymentTx);

        // Credit creators for each order item
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElse(item.getProduct());

            User seller = product.getSeller();
            Wallet sellerWallet = walletRepository.findByUser(seller)
                    .orElseGet(() -> {
                        Wallet newWallet = Wallet.builder()
                                .user(seller)
                                .balance(BigDecimal.ZERO)
                                .createdAt(LocalDateTime.now())
                                .build();
                        return walletRepository.save(newWallet);
                    });

            BigDecimal itemTotalPrice = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            sellerWallet.setBalance(sellerWallet.getBalance().add(itemTotalPrice));
            walletRepository.save(sellerWallet);

            // Record transaction for seller
            Transaction sellerTx = Transaction.builder()
                    .wallet(sellerWallet)
                    .amount(itemTotalPrice)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.COMPLETED)
                    .description("Payout for Product " + product.getName() + " in Order " + order.getOrderNumber())
                    .product(product)
                    .createdAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();
            transactionRepository.save(sellerTx);

            // Notify seller
            notificationService.createNotification(
                    seller,
                    "Product Purchased",
                    "A customer has purchased " + item.getQuantity() + " unit(s) of " + product.getName() + ". Payout of $" + itemTotalPrice + " has been credited to your wallet.",
                    NotificationType.ORDER_STATUS
            );
        }

        // Mark reservations as completed
        for (Reservation res : activeReservations) {
            res.setStatus(ReservationStatus.COMPLETED);
            res.setCompletedAt(LocalDateTime.now());
            reservationRepository.save(res);
        }
        
        // Update order status
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);

        // Notify buyer
        notificationService.createNotification(
                currentUser,
                "Order Paid Successfully",
                "Your payment of $" + order.getTotal() + " for order " + order.getOrderNumber() + " was processed successfully.",
                NotificationType.ORDER_STATUS
        );

        return mapToOrderResponse(updatedOrder);
    }

    /**
     * Get all orders for the current user
     * @param pageable pagination information
     * @return page of order responses
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        Page<Order> orders = orderRepository.findByUser(currentUser, pageable);
        
        return orders.map(this::mapToOrderResponse);
    }

    /**
     * Get an order by ID
     * @param orderId the order ID
     * @return the order response
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        User currentUser = userService.getCurrentUser();
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        
        // Check if the order belongs to the current user
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("You can only view your own orders", HttpStatus.FORBIDDEN);
        }
        
        return mapToOrderResponse(order);
    }

    /**
     * Cancel an order
     * @param orderId the order ID
     * @return the updated order response
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        User currentUser = userService.getCurrentUser();
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        
        // Check if the order belongs to the current user
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("You can only cancel your own orders", HttpStatus.FORBIDDEN);
        }
        
        // Check if order can be cancelled
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppException("Only pending payment orders can be cancelled", HttpStatus.BAD_REQUEST);
        }
        
        // Update order status
        order.setStatus(OrderStatus.CANCELLED);
        
        Order updatedOrder = orderRepository.save(order);
        return mapToOrderResponse(updatedOrder);
    }

    /**
     * Map an Order entity to an OrderResponse DTO
     * @param order the order entity
     * @return the order response DTO
     */
    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        List<OrderItemResponse> orderItemResponses = orderItems.stream()
                .map(this::mapToOrderItemResponse)
                .collect(Collectors.toList());
        
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .userName(order.getUser().getFullName())
                .status(order.getStatus())
                .deliveryOption(order.getDeliveryOption())
                .location(order.getLocation() != null ? mapToLocationResponse(order.getLocation()) : null)
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .notes(order.getNotes())
                .items(orderItemResponses)
                .itemCount(orderItems.size())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .paidAt(order.getPaidAt())
                .completedAt(order.getCompletedAt())
                .isPaid(order.getPaidAt() != null)
                .isCompleted(order.getCompletedAt() != null)
                .build();
    }

    /**
     * Map an OrderItem entity to an OrderItemResponse DTO
     * @param orderItem the order item entity
     * @return the order item response DTO
     */
    private OrderItemResponse mapToOrderItemResponse(OrderItem orderItem) {
        return OrderItemResponse.builder()
                .id(orderItem.getId())
                .productId(orderItem.getProduct().getId())
                .productName(orderItem.getProductName())
                .price(orderItem.getPrice())
                .quantity(orderItem.getQuantity())
                .subtotal(orderItem.getSubtotal())
                .notes(orderItem.getNotes())
                .build();
    }
    
    /**
     * Map a Location entity to a LocationResponse DTO
     * @param location the location entity
     * @return the location response DTO
     */
    private com.celebstash.backend.dto.location.LocationResponse mapToLocationResponse(Location location) {
        return com.celebstash.backend.dto.location.LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .city(location.getCity())
                .state(location.getState())
                .zipCode(location.getZipCode())
                .country(location.getCountry())
                .deliveryFee(location.getDeliveryFee())
                .active(location.getActive())
                .userId(location.getUser().getId())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}