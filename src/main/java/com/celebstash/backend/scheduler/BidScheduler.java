package com.celebstash.backend.scheduler;

import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.Transaction;
import com.celebstash.backend.model.enums.ProductStatus;
import com.celebstash.backend.model.enums.ProductType;
import com.celebstash.backend.model.enums.TransactionStatus;
import com.celebstash.backend.model.enums.TransactionType;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.repository.ProductRepository;
import com.celebstash.backend.repository.TransactionRepository;
import com.celebstash.backend.service.WalletService;
import com.celebstash.backend.service.NotificationService;
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
public class BidScheduler {

    private final ProductRepository productRepository;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    /**
     * Check for completed bids every minute
     * This method finds all bidding products with an end time in the past
     * and processes them.
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    public void checkCompletedBids() {
        log.info("Checking for completed bids...");

        LocalDateTime now = LocalDateTime.now();

        // Find all bidding products with an end time in the past
        List<Product> completedBids = productRepository.findByProductTypeAndBidEndTimeBefore(
                ProductType.BIDDING, now);

        // Filter for those that are still in approved/active status to prevent repeat processing
        List<Product> activeCompletedBids = completedBids.stream()
                .filter(p -> p.getStatus() == ProductStatus.APPROVED)
                .toList();

        log.info("Found {} completed bids requiring processing", activeCompletedBids.size());

        // Process each completed bid
        for (Product product : activeCompletedBids) {
            processCompletedBid(product);
        }
    }

    /**
     * Process a completed bid
     * This method transfers the payout, sets the product status, and notifies the users.
     * @param product the product with a completed bid
     */
    private void processCompletedBid(Product product) {
        if (product.getCurrentBidder() != null) {
            log.info("Bid completed for product {}: Winner is {} with bid amount {}",
                    product.getId(), product.getCurrentBidder().getFullName(), product.getCurrentBidPrice());

            // Process the financial transfer (deduct held balance of winner, credit seller minus platform commission)
            walletService.completeBidPayout(
                    product.getCurrentBidder().getId(),
                    product.getSeller().getId(),
                    product.getCurrentBidPrice(),
                    product.getId()
            );

            // Find all pending bid transactions for this product
            List<Transaction> bidTransactions = transactionRepository.findByProductAndTypeAndStatus(
                    product, TransactionType.BID, TransactionStatus.PENDING);

            // Process each transaction status
            for (Transaction transaction : bidTransactions) {
                // If this is the winner's transaction, mark it as completed
                if (transaction.getWallet().getUser().getId().equals(product.getCurrentBidder().getId())) {
                    transaction.setStatus(TransactionStatus.COMPLETED);
                    transaction.setCompletedAt(LocalDateTime.now());
                    transactionRepository.save(transaction);

                    log.info("Completed bid transaction for winner: {}", transaction.getId());
                } else {
                    // For losers, refund their bid amount
                    walletService.refundBid(transaction.getId());
                    log.info("Refunded bid for user: {}", transaction.getWallet().getUser().getId());
                }
            }

            // Mark the product as SOLD_OUT
            product.setStatus(ProductStatus.SOLD_OUT);
            productRepository.save(product);

            // Notify winner
            notificationService.createNotification(
                    product.getCurrentBidder(),
                    "Auction Won!",
                    "Congratulations! You won the auction for '" + product.getName() + "' with a bid of $" + product.getCurrentBidPrice() + ".",
                    NotificationType.ORDER_STATUS
            );

            // Notify seller
            notificationService.createNotification(
                    product.getSeller(),
                    "Auction Item Sold",
                    "Your auction item '" + product.getName() + "' was successfully sold to " + product.getCurrentBidder().getFullName() + " for $" + product.getCurrentBidPrice() + ".",
                    NotificationType.ORDER_STATUS
            );

        } else {
            log.info("Bid completed for product {} with no bids. Returning to standard shop.", product.getId());

            // Return to regular shop as standard item if no bids were received
            product.setProductType(ProductType.REGULAR);
            product.setStatus(ProductStatus.APPROVED);
            product.setBidStartTime(null);
            product.setBidEndTime(null);
            productRepository.save(product);
        }
    }
}
