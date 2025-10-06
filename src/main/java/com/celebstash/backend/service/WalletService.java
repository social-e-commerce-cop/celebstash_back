package com.celebstash.backend.service;

import com.celebstash.backend.dto.wallet.TopUpRequest;
import com.celebstash.backend.dto.wallet.TransactionResponse;
import com.celebstash.backend.dto.wallet.WalletResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.Transaction;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.TransactionStatus;
import com.celebstash.backend.model.enums.TransactionType;
import com.celebstash.backend.repository.ProductRepository;
import com.celebstash.backend.repository.TransactionRepository;
import com.celebstash.backend.repository.WalletRepository;
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
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    /** Get or create wallet for current user */
    @Transactional
    public Wallet getOrCreateWallet() {
        User currentUser = userService.getCurrentUser();
        return walletRepository.findByUser(currentUser)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .user(currentUser)
                            .balance(BigDecimal.ZERO)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    /** Get wallet info */
    @Transactional(readOnly = true)
    public WalletResponse getWalletInfo() {
        return mapToWalletResponse(getOrCreateWallet());
    }

    /** Top up wallet */
    @Transactional
    public WalletResponse topUpWallet(TopUpRequest request) {
        Wallet wallet = getOrCreateWallet();
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description(request.getDescription() != null ? request.getDescription() : "Wallet top-up")
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        walletRepository.save(wallet);
        return mapToWalletResponse(wallet);
    }

    /** Check if wallet has sufficient balance */
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(BigDecimal amount) {
        return getOrCreateWallet().getBalance().compareTo(amount) >= 0;
    }

    /** Reserve funds for a bid */
    @Transactional
    public TransactionResponse reserveFundsForBid(BigDecimal amount, Long productId) {
        Wallet wallet = getOrCreateWallet();
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException("Insufficient balance for bidding", HttpStatus.BAD_REQUEST);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        wallet.setBalance(wallet.getBalance().subtract(amount));

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.BID)
                .status(TransactionStatus.PENDING)
                .description("Bid on " + product.getName())
                .product(product)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        walletRepository.save(wallet);

        return mapToTransactionResponse(transaction);
    }

    /** Refund a bid by transaction ID (used by BidScheduler) */
    @Transactional
    public WalletResponse refundBid(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException("Transaction not found", HttpStatus.NOT_FOUND));

        if (transaction.getType() != TransactionType.BID || transaction.getStatus() != TransactionStatus.PENDING) {
            throw new AppException("Transaction is not a pending bid", HttpStatus.BAD_REQUEST);
        }

        // Mark original transaction as refunded
        transaction.setStatus(TransactionStatus.REFUNDED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        // Refund wallet
        Wallet wallet = transaction.getWallet();
        wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Record refund transaction
        Transaction refundTransaction = Transaction.builder()
                .wallet(wallet)
                .amount(transaction.getAmount())
                .type(TransactionType.BID_REFUND)
                .status(TransactionStatus.COMPLETED)
                .description("Refund for outbid bid on " +
                        (transaction.getProduct() != null ? transaction.getProduct().getName() : "product"))
                .product(transaction.getProduct())
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(refundTransaction);

        return mapToWalletResponse(wallet);
    }

    @Transactional
    public WalletResponse deductFunds(BigDecimal amount, String description, String pin) {
        Wallet wallet = getOrCreateWallet();

        // Check if PIN matches
        if (!hasPinSet() || !wallet.getPin().equals(pin)) {
            throw new AppException("Invalid PIN", HttpStatus.BAD_REQUEST);
        }

        // Check balance
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }

        // Deduct funds
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setUpdatedAt(LocalDateTime.now());

        // Create transaction
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .description(description != null ? description : "Payment")
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        walletRepository.save(wallet);

        return mapToWalletResponse(wallet);
    }


    /**
     * Refund a previous bidder's reserved bid amount
     * @param userId the ID of the user to refund
     * @param amount the bid amount to refund
     * @return updated wallet response
     */
    @Transactional
    public WalletResponse refundReservedFunds(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException("Wallet not found for user " + userId, HttpStatus.NOT_FOUND));

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setUpdatedAt(LocalDateTime.now());

        Transaction refundTransaction = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.BID_REFUND)
                .status(TransactionStatus.COMPLETED)
                .description("Refund for outbid amount")
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(refundTransaction);
        walletRepository.save(wallet);

        return mapToWalletResponse(wallet);
    }


    /** Get transaction history */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory() {
        Wallet wallet = getOrCreateWallet();
        List<Transaction> transactions = transactionRepository.findByWalletOrderByCreatedAtDesc(wallet);
        return transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    /** Set or update wallet PIN */
    @Transactional
    public WalletResponse setPin(String pin) {
        validatePin(pin);
        Wallet wallet = getOrCreateWallet();
        wallet.setPin(pin);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        return mapToWalletResponse(wallet);
    }

    /** Check if wallet PIN is set */
    @Transactional(readOnly = true)
    public boolean hasPinSet() {
        Wallet wallet = getOrCreateWallet();
        return wallet.getPin() != null && !wallet.getPin().isEmpty();
    }

    /** Validate PIN format */
    private void validatePin(String pin) {
        if (pin == null || pin.isEmpty()) {
            throw new AppException("PIN is required", HttpStatus.BAD_REQUEST);
        }
        if (pin.length() < 4 || pin.length() > 6 || !pin.matches("\\d+")) {
            throw new AppException("PIN must be 4-6 digits", HttpStatus.BAD_REQUEST);
        }
    }

    /** Map Wallet to DTO */
    private WalletResponse mapToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .userName(wallet.getUser().getFullName())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    /** Map Transaction to DTO */
    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .walletId(transaction.getWallet().getId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .productId(transaction.getProduct() != null ? transaction.getProduct().getId() : null)
                .productName(transaction.getProduct() != null ? transaction.getProduct().getName() : null)
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}
