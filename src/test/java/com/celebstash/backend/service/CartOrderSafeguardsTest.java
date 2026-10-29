package com.celebstash.backend.service;

import com.celebstash.backend.dto.order.OrderRequest;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartOrderSafeguardsTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock LocationRepository locationRepository;
    @Mock CartService cartService;
    @Mock UserService userService;
    @Mock WalletService walletService;
    @Mock WalletRepository walletRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock ProductRepository productRepository;
    @Mock NotificationService notificationService;

    @InjectMocks OrderService orderService;

    private User buyer;
    private User stranger;
    private Product product;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(1L);
        stranger = new User();
        stranger.setId(2L);
        product = Product.builder().id(5L).name("Tour Hoodie").price(new BigDecimal("40.00"))
                .stockQuantity(3).status(ProductStatus.APPROVED).seller(stranger).build();
        wallet = Wallet.builder().id(9L).user(buyer).balance(new BigDecimal("80.00"))
                .heldBalance(new BigDecimal("20.00")).createdAt(LocalDateTime.now()).build();
        when(userService.getCurrentUser()).thenReturn(buyer);
        when(walletRepository.findByUser(buyer)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(wallet));
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
    }

    private Reservation pendingReservation(int qty) {
        return Reservation.builder().id(3L).user(buyer).product(product).quantity(qty)
                .heldAmount(new BigDecimal("20.00")).requiredAmount(new BigDecimal("40.00"))
                .status(ReservationStatus.PENDING).expiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private Cart cartWith(CartItem item) {
        Cart cart = Cart.builder().id(4L).user(buyer).items(new ArrayList<>(List.of(item))).build();
        item.setCart(cart);
        when(cartService.getOrCreateCart()).thenReturn(cart);
        return cart;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        AppException ex = assertThrows(AppException.class, action::run);
        assertEquals(expected, ex.getStatus());
    }

    @Test
    void orderIsRefusedWhenAProductWasWithdrawn() {
        product.setStatus(ProductStatus.REJECTED);
        cartWith(CartItem.builder().product(product).quantity(1).reservation(pendingReservation(1)).build());

        assertStatus(HttpStatus.CONFLICT, () -> orderService.createOrder(
                OrderRequest.builder().deliveryOption(DeliveryOption.SELF_PICKUP).build()));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void orderIsRefusedWhenTheReservationExpired() {
        Reservation expired = pendingReservation(1);
        expired.setStatus(ReservationStatus.EXPIRED);
        cartWith(CartItem.builder().product(product).quantity(1).reservation(expired).build());

        assertStatus(HttpStatus.CONFLICT, () -> orderService.createOrder(
                OrderRequest.builder().deliveryOption(DeliveryOption.SELF_PICKUP).build()));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void anotherUsersAddressCannotBeUsed() {
        cartWith(CartItem.builder().product(product).quantity(1).reservation(pendingReservation(1)).build());
        Location foreign = Location.builder().id(6L).user(stranger).deliveryFee(BigDecimal.ZERO).build();
        when(locationRepository.findById(6L)).thenReturn(Optional.of(foreign));

        assertStatus(HttpStatus.NOT_FOUND, () -> orderService.createOrder(
                OrderRequest.builder().deliveryOption(DeliveryOption.DELIVERY).locationId(6L).build()));
    }

    @Test
    void onlyTheBuyerCanPayForAnOrder() {
        Order order = Order.builder().id(7L).user(stranger).status(OrderStatus.PENDING_PAYMENT)
                .total(new BigDecimal("40.00")).build();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertStatus(HttpStatus.NOT_FOUND, () -> orderService.processPayment(7L, "1234"));
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void wrongPinDoesNotMarkTheOrderPaid() {
        wallet.setPin("1111");
        Order order = Order.builder().id(8L).user(buyer).status(OrderStatus.PENDING_PAYMENT)
                .total(new BigDecimal("40.00")).build();
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        assertStatus(HttpStatus.BAD_REQUEST, () -> orderService.processPayment(8L, "9999"));
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("80.00"), wallet.getBalance());
    }

    @Test
    void cancellingAnUnpaidOrderReleasesStockAndEscrow() {
        Order order = Order.builder().id(9L).user(buyer).status(OrderStatus.PENDING_PAYMENT).build();
        Reservation res = pendingReservation(1);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of(
                OrderItem.builder().order(order).product(product).quantity(1).price(product.getPrice()).build()));
        when(reservationRepository.findByUserAndProductAndStatus(buyer, product, ReservationStatus.PENDING))
                .thenReturn(Optional.of(res));

        orderService.cancelOrder(9L);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(ReservationStatus.CANCELLED, res.getStatus());
        assertEquals(4, product.getStockQuantity());
        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
        assertEquals(new BigDecimal("0.00"), wallet.getHeldBalance());
    }

    @Test
    void cartRefusesAProductAlreadyHeldInAnotherSize() {
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        CartService realCart = new CartService(cartRepository, cartItemRepository, productRepository,
                userService, walletService, reservationRepository, walletRepository);

        product.setSizeStock(Map.of("M", 2, "L", 1));
        Cart cart = Cart.builder().id(4L).user(buyer).items(new ArrayList<>()).build();
        when(cartRepository.findByUser(buyer)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartAndProduct(cart, product)).thenReturn(Optional.of(
                CartItem.builder().cart(cart).product(product).quantity(1).selectedSize("M").build()));

        assertStatus(HttpStatus.CONFLICT, () -> realCart.addProductToCart(5L, 1, "L", null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> realCart.addProductToCart(5L, 1, "XXL", null));
        assertEquals(new BigDecimal("80.00"), wallet.getBalance(), "no escrow is taken for a refused add");
        assertEquals(3, product.getStockQuantity(), "no stock is locked for a refused add");
    }
}
