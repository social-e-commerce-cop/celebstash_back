package com.celebstash.backend.scheduler;

import com.celebstash.backend.model.CartItem;
import com.celebstash.backend.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartCleanupScheduler {

    private final CartItemRepository cartItemRepository;

    /**
     * Remove expired cart items every hour
     * This method finds all cart items that have been in the cart for more than 24 hours
     * and removes them
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void removeExpiredCartItems() {
        log.info("Checking for expired cart items...");
        
        LocalDateTime expirationTime = LocalDateTime.now().minusHours(24);
        
        // Find all cart items added before the expiration time
        List<CartItem> expiredItems = cartItemRepository.findByAddedAtBefore(expirationTime);
        
        if (!expiredItems.isEmpty()) {
            log.info("Found {} expired cart items to remove", expiredItems.size());
            
            // Delete all expired items
            cartItemRepository.deleteAll(expiredItems);
            
            log.info("Successfully removed {} expired cart items", expiredItems.size());
        } else {
            log.info("No expired cart items found");
        }
    }
}