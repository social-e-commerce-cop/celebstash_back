package com.celebstash.backend.scheduler;

import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.model.enums.ReservationStatus;
import com.celebstash.backend.repository.*;
import com.celebstash.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final WalletRepository walletRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final NotificationService notificationService;

    /**
     * Scan for expired reservations every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 120,000 ms = 2 minutes
    @Transactional
    public void processExpiredReservations() {
        log.info("Scanning for expired reservations...");

        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expiredReservations = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING, now
        );

        if (!expiredReservations.isEmpty()) {
            log.info("Found {} expired reservations to release", expiredReservations.size());

            for (Reservation res : expiredReservations) {
                try {
                    // 1. Release stock lock using row lock
                    productRepository.findByIdForUpdate(res.getProduct().getId()).ifPresent(product -> {
                        product.setStockQuantity(product.getStockQuantity() + res.getQuantity());
                        productRepository.save(product);
                        log.debug("Released stock lock of {} units for product {}", res.getQuantity(), product.getName());
                    });

                    // 2. Refund escrow hold
                    User user = res.getUser();
                    walletRepository.findByUser(user).ifPresent(wallet -> {
                        wallet.setBalance(wallet.getBalance().add(res.getHeldAmount()));
                        wallet.setHeldBalance(wallet.getHeldBalance().subtract(res.getHeldAmount()));
                        walletRepository.save(wallet);
                        log.debug("Refunded reservation escrow hold of ${} to user {}", res.getHeldAmount(), user.getFullName());
                    });

                    // 3. Mark status as EXPIRED
                    res.setStatus(ReservationStatus.EXPIRED);
                    reservationRepository.save(res);

                    // 4. Clean up cart item
                    cartRepository.findByUser(user).ifPresent(cart -> {
                        Optional<CartItem> itemOpt = cartItemRepository.findByCartAndProduct(cart, res.getProduct());
                        itemOpt.ifPresent(cartItem -> {
                            cartItemRepository.delete(cartItem);
                            log.debug("Removed expired reserved item {} from user {} cart", res.getProduct().getName(), user.getFullName());
                        });
                    });

                    // 5. Send notification to the user
                    notificationService.createNotification(
                            user,
                            "Reservation Expired",
                            "Your reservation for product " + res.getProduct().getName() + " has expired. The product has been returned to listing, and your escrow hold of $" + res.getHeldAmount() + " has been refunded to your wallet.",
                            NotificationType.CART_EXPIRING_SOON
                    );

                    log.info("Successfully expired reservation ID {} for product {}", res.getId(), res.getProduct().getName());
                } catch (Exception e) {
                    log.error("Error expiring reservation ID " + res.getId(), e);
                }
            }
        } else {
            log.debug("No expired reservations found");
        }
    }
}
