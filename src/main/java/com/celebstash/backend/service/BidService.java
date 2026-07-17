package com.celebstash.backend.service;

import com.celebstash.backend.dto.bid.BidRequest;
import com.celebstash.backend.dto.bid.BidResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ProductStatus;
import com.celebstash.backend.model.enums.ProductType;
import com.celebstash.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final ProductRepository productRepository;
    private final UserService userService;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public List<BidResponse> getBiddingProducts() {
        List<Product> biddingProducts = productRepository.findByProductTypeAndStatus(ProductType.BIDDING, ProductStatus.APPROVED);
        return biddingProducts.stream().map(this::mapToBidResponse).collect(Collectors.toList());
    }

    @Transactional
    public BidResponse placeBid(BidRequest request) {
        User currentUser = userService.getCurrentUser();
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        if (product.getProductType() != ProductType.BIDDING)
            throw new AppException("Product is not available for bidding", HttpStatus.BAD_REQUEST);

        if (product.getStatus() != ProductStatus.APPROVED)
            throw new AppException("Product is not approved for bidding", HttpStatus.BAD_REQUEST);

        if (product.getBidEndTime() != null && product.getBidEndTime().isBefore(LocalDateTime.now()))
            throw new AppException("Bidding has ended for this product", HttpStatus.BAD_REQUEST);

        // Determine the current price threshold
        BigDecimal currentPrice = product.getCurrentBidPrice() != null ? product.getCurrentBidPrice() : product.getInitialBidPrice();
        if (currentPrice == null) {
            currentPrice = product.getPrice();
        }

        // Calculate minimum required increment: max of $5.00 or 5% of current price
        BigDecimal minIncrement = currentPrice.multiply(new BigDecimal("0.05"));
        BigDecimal flatMinIncrement = new BigDecimal("5.00");
        if (minIncrement.compareTo(flatMinIncrement) < 0) {
            minIncrement = flatMinIncrement;
        }

        BigDecimal minRequiredBid = currentPrice.add(minIncrement);
        if (request.getBidAmount().compareTo(minRequiredBid) < 0) {
            throw new AppException("Bid amount must be at least $" + minRequiredBid.setScale(2, java.math.RoundingMode.HALF_UP) + " (minimum increment is $" + minIncrement.setScale(2, java.math.RoundingMode.HALF_UP) + ")", HttpStatus.BAD_REQUEST);
        }

        // If there is a previous bidder, refund them
        if (product.getCurrentBidder() != null) {
            walletService.refundReservedFunds(product.getCurrentBidder().getId(), product.getCurrentBidPrice());
        }

        // Reserve funds for current bid
        walletService.reserveFundsForBid(request.getBidAmount(), product.getId());

        // Update product with new bid
        product.setCurrentBidPrice(request.getBidAmount());
        product.setCurrentBidder(currentUser);

        if (product.getBidStartTime() == null) {
            product.setBidStartTime(LocalDateTime.now());
            product.setBidEndTime(LocalDateTime.now().plusHours(24));
        } else {
            // Anti-sniping: extend by 2 minutes if bid lands in the final 60 seconds of the auction
            LocalDateTime bidEndTime = product.getBidEndTime();
            if (bidEndTime != null && LocalDateTime.now().isAfter(bidEndTime.minusMinutes(1))) {
                product.setBidEndTime(bidEndTime.plusMinutes(2));
                log.info("Anti-sniping triggered. Bid end time extended for product: {}", product.getId());
            }
        }

        Product updatedProduct = productRepository.save(product);
        return mapToBidResponse(updatedProduct);
    }

    @Transactional(readOnly = true)
    public BidResponse getBidDetails(Long productId) {
        User currentUser = userService.getCurrentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        if (product.getProductType() != ProductType.BIDDING)
            throw new AppException("Product is not a bidding product", HttpStatus.BAD_REQUEST);

        BidResponse response = mapToBidResponse(product);
        if (product.getCurrentBidder() != null && product.getCurrentBidder().getId().equals(currentUser.getId())) {
            response.setWinner(isBidWinner(product, currentUser));
        }

        return response;
    }

    private boolean isBidWinner(Product product, User user) {
        return product.getBidEndTime() != null &&
                product.getBidEndTime().isBefore(LocalDateTime.now()) &&
                product.getCurrentBidder() != null &&
                product.getCurrentBidder().getId().equals(user.getId());
    }

    private BidResponse mapToBidResponse(Product product) {
        boolean isActive = product.getBidStartTime() != null &&
                (product.getBidEndTime() == null || product.getBidEndTime().isAfter(LocalDateTime.now()));

        String bidStatus = "NOT_STARTED";
        if (product.getBidStartTime() != null) bidStatus = isActive ? "ACTIVE" : "EXPIRED";

        return BidResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productDescription(product.getDescription())
                .initialBidPrice(product.getInitialBidPrice())
                .currentBidPrice(product.getCurrentBidPrice())
                .currentBidderId(product.getCurrentBidder() != null ? product.getCurrentBidder().getId() : null)
                .currentBidderName(product.getCurrentBidder() != null ? product.getCurrentBidder().getFullName() : null)
                .bidStartTime(product.getBidStartTime())
                .bidEndTime(product.getBidEndTime())
                .isActive(isActive)
                .bidStatus(bidStatus)
                .build();
    }
}
