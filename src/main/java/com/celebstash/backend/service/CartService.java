package com.celebstash.backend.service;

import com.celebstash.backend.dto.cart.CartItemResponse;
import com.celebstash.backend.dto.cart.CartResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Cart;
import com.celebstash.backend.model.CartItem;
import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.Reservation;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.ProductStatus;
import com.celebstash.backend.model.enums.ReservationStatus;
import com.celebstash.backend.repository.CartItemRepository;
import com.celebstash.backend.repository.CartRepository;
import com.celebstash.backend.repository.ProductRepository;
import com.celebstash.backend.repository.ReservationRepository;
import com.celebstash.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final WalletService walletService;
    private final ReservationRepository reservationRepository;
    private final WalletRepository walletRepository;

    /**
     * Get or create a cart for the current user
     * @return the user's cart
     */
    @Transactional
    public Cart getOrCreateCart() {
        User currentUser = userService.getCurrentUser();
        return cartRepository.findByUser(currentUser)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .user(currentUser)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    /**
     * Add a product to the user's cart
     * @param productId the ID of the product to add
     * @param quantity the quantity to add
     * @return the updated cart
     */
    @Transactional
    public CartResponse addProductToCart(Long productId, Integer quantity) {
        if (quantity <= 0) {
            throw new AppException("Quantity must be greater than 0", HttpStatus.BAD_REQUEST);
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        // Only approved products can be added to cart
        if (product.getStatus() != ProductStatus.APPROVED) {
            throw new AppException("Product is not available for purchase", HttpStatus.BAD_REQUEST);
        }

        // Check if there's enough stock
        if (product.getStockQuantity() < quantity) {
            throw new AppException("Not enough stock available", HttpStatus.BAD_REQUEST);
        }

        // Calculate total price for this product
        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        BigDecimal heldAmount = totalPrice.multiply(new BigDecimal("0.50"));

        // Check if user has sufficient balance (50% escrow hold)
        User currentUser = userService.getCurrentUser();
        Wallet wallet = walletService.getOrCreateWallet();
        if (wallet.getBalance().compareTo(heldAmount) < 0) {
            throw new AppException("Insufficient balance. A 50% reservation escrow ($" + heldAmount.setScale(2, java.math.RoundingMode.HALF_UP) + ") is required.", HttpStatus.BAD_REQUEST);
        }

        // Deduct escrow hold
        wallet.setBalance(wallet.getBalance().subtract(heldAmount));
        wallet.setHeldBalance(wallet.getHeldBalance().add(heldAmount));
        walletRepository.save(wallet);

        // Place stock lock
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        Cart cart = getOrCreateCart();

        // Check if product already exists in cart
        CartItem existingItem = cartItemRepository.findByCartAndProduct(cart, product).orElse(null);

        if (existingItem != null) {
            Reservation existingRes = existingItem.getReservation();
            if (existingRes != null && existingRes.getStatus() == ReservationStatus.PENDING) {
                existingRes.setQuantity(existingRes.getQuantity() + quantity);
                existingRes.setHeldAmount(existingRes.getHeldAmount().add(heldAmount));
                existingRes.setRequiredAmount(existingRes.getRequiredAmount().add(totalPrice));
                existingRes.setExpiresAt(LocalDateTime.now().plusHours(24));
                reservationRepository.save(existingRes);
            }
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            cartItemRepository.save(existingItem);
        } else {
            // Create a Reservation
            Reservation reservation = Reservation.builder()
                    .user(currentUser)
                    .product(product)
                    .quantity(quantity)
                    .heldAmount(heldAmount)
                    .requiredAmount(totalPrice)
                    .status(ReservationStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();
            reservation = reservationRepository.save(reservation);

            // Add new item to cart
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .reservation(reservation)
                    .addedAt(LocalDateTime.now())
                    .build();
            cartItemRepository.save(newItem);
        }

        return mapToCartResponse(cart);
    }

    /**
     * Remove a product from the user's cart
     * @param productId the ID of the product to remove
     * @return the updated cart
     */
    @Transactional
    public CartResponse removeProductFromCart(Long productId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        Cart cart = getOrCreateCart();

        CartItem cartItem = cartItemRepository.findByCartAndProduct(cart, product).orElse(null);
        if (cartItem != null) {
            Reservation reservation = cartItem.getReservation();
            if (reservation != null && reservation.getStatus() == ReservationStatus.PENDING) {
                // Release stock lock
                product.setStockQuantity(product.getStockQuantity() + reservation.getQuantity());
                productRepository.save(product);

                // Refund wallet hold
                Wallet wallet = walletService.getOrCreateWallet();
                wallet.setBalance(wallet.getBalance().add(reservation.getHeldAmount()));
                wallet.setHeldBalance(wallet.getHeldBalance().subtract(reservation.getHeldAmount()));
                walletRepository.save(wallet);

                // Update reservation status
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservationRepository.save(reservation);
            }
            cartItemRepository.delete(cartItem);
        }

        return mapToCartResponse(cart);
    }

    /**
     * Update the quantity of a product in the cart
     * @param productId the ID of the product to update
     * @param quantity the new quantity
     * @return the updated cart
     */
    @Transactional
    public CartResponse updateCartItemQuantity(Long productId, Integer quantity) {
        if (quantity <= 0) {
            return removeProductFromCart(productId);
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        Cart cart = getOrCreateCart();
        CartItem cartItem = cartItemRepository.findByCartAndProduct(cart, product)
                .orElseThrow(() -> new AppException("Product not found in cart", HttpStatus.NOT_FOUND));

        int diff = quantity - cartItem.getQuantity();
        if (diff == 0) {
            return mapToCartResponse(cart);
        }

        Reservation reservation = cartItem.getReservation();
        Wallet wallet = walletService.getOrCreateWallet();

        BigDecimal unitPrice = product.getPrice();
        BigDecimal diffPrice = unitPrice.multiply(BigDecimal.valueOf(Math.abs(diff)));
        BigDecimal diffHeld = diffPrice.multiply(new BigDecimal("0.50"));

        if (diff > 0) {
            // Check stock
            if (product.getStockQuantity() < diff) {
                throw new AppException("Not enough stock available", HttpStatus.BAD_REQUEST);
            }
            // Check wallet balance
            if (wallet.getBalance().compareTo(diffHeld) < 0) {
                throw new AppException("Insufficient wallet balance for updating reservation escrow", HttpStatus.BAD_REQUEST);
            }

            // Deduct
            wallet.setBalance(wallet.getBalance().subtract(diffHeld));
            wallet.setHeldBalance(wallet.getHeldBalance().add(diffHeld));

            // Soft lock stock
            product.setStockQuantity(product.getStockQuantity() - diff);
        } else {
            // Refund difference
            wallet.setBalance(wallet.getBalance().add(diffHeld));
            wallet.setHeldBalance(wallet.getHeldBalance().subtract(diffHeld));

            // Release stock lock
            product.setStockQuantity(product.getStockQuantity() + Math.abs(diff));
        }

        // Save wallet and product
        walletRepository.save(wallet);
        productRepository.save(product);

        // Update reservation
        if (reservation != null && reservation.getStatus() == ReservationStatus.PENDING) {
            reservation.setQuantity(quantity);
            reservation.setHeldAmount(reservation.getHeldAmount().add(diff > 0 ? diffHeld : diffHeld.negate()));
            reservation.setRequiredAmount(reservation.getRequiredAmount().add(diff > 0 ? diffPrice : diffPrice.negate()));
            reservation.setExpiresAt(LocalDateTime.now().plusHours(24));
            reservationRepository.save(reservation);
        }

        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);

        return mapToCartResponse(cart);
    }

    /**
     * Get all items in the user's cart
     * @return cart response with items
     */
    @Transactional
    public CartResponse getCartItems() {
        Cart cart = getOrCreateCart();
        return mapToCartResponse(cart);
    }

    /**
     * Clear the user's cart
     */
    @Transactional
    public void clearCart() {
        Cart cart = getOrCreateCart();
        cartItemRepository.deleteByCart(cart);
    }

    private void expireCartItem(CartItem item) {
        Reservation reservation = item.getReservation();
        if (reservation != null && reservation.getStatus() == ReservationStatus.PENDING) {
            Product product = item.getProduct();
            productRepository.findByIdForUpdate(product.getId()).ifPresent(p -> {
                p.setStockQuantity(p.getStockQuantity() + reservation.getQuantity());
                productRepository.save(p);
            });

            User user = reservation.getUser();
            walletRepository.findByUser(user).ifPresent(wallet -> {
                wallet.setBalance(wallet.getBalance().add(reservation.getHeldAmount()));
                wallet.setHeldBalance(wallet.getHeldBalance().subtract(reservation.getHeldAmount()));
                walletRepository.save(wallet);
            });

            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
        }
        cartItemRepository.delete(item);
    }

    /**
     * Map a Cart entity to a CartResponse DTO
     * @param cart the cart entity
     * @return the cart response DTO
     */
    private CartResponse mapToCartResponse(Cart cart) {
        // Get all cart items
        List<CartItem> allCartItems = cartItemRepository.findByCart(cart);

        // Filter out expired items (older than 24 hours)
        LocalDateTime expirationTime = LocalDateTime.now().minusHours(24);
        List<CartItem> validCartItems = allCartItems.stream()
                .filter(item -> item.getAddedAt() == null || item.getAddedAt().isAfter(expirationTime))
                .collect(Collectors.toList());

        // Remove expired items from the cart and refund holds
        allCartItems.stream()
                .filter(item -> item.getAddedAt() != null && item.getAddedAt().isBefore(expirationTime))
                .forEach(this::expireCartItem);

        // Map valid items to response
        List<CartItemResponse> cartItemResponses = validCartItems.stream()
                .map(this::mapToCartItemResponse)
                .collect(Collectors.toList());

        // Calculate total items and total price
        int totalItems = cartItemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        BigDecimal totalPrice = cartItemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(cartItemResponses)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .build();
    }

    /**
     * Map a CartItem entity to a CartItemResponse DTO
     * @param cartItem the cart item entity
     * @return the cart item response DTO
     */
    private CartItemResponse mapToCartItemResponse(CartItem cartItem) {
        Product product = cartItem.getProduct();
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return CartItemResponse.builder()
                .id(cartItem.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productDescription(product.getDescription())
                .productImageUrl(product.getImageUrls() != null && !product.getImageUrls().isEmpty() ? 
                        product.getImageUrls().get(0) : null)
                .productPrice(product.getPrice())
                .quantity(cartItem.getQuantity())
                .subtotal(subtotal)
                .build();
    }
}
