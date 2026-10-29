package com.celebstash.backend.service;

import com.celebstash.backend.dto.wallet.*;
import com.celebstash.backend.dto.wallet.xentripay.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.Transaction;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.PaymentProvider;
import com.celebstash.backend.model.enums.TransactionStatus;
import com.celebstash.backend.model.enums.TransactionType;
import com.celebstash.backend.repository.ProductRepository;
import com.celebstash.backend.repository.TransactionRepository;
import com.celebstash.backend.repository.UserRepository;
import com.celebstash.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final UserRepository userRepository;
    private final UserService userService;
    private final MomoGatewayService momoGatewayService;
    private final XentripayService xentripayService;

    /** Get or create wallet for current user */
    @Transactional
    public Wallet getOrCreateWallet() {
        User currentUser = userService.getCurrentUser();
        return walletRepository.findByUser(currentUser)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .user(currentUser)
                            .balance(BigDecimal.ZERO)
                            .heldBalance(BigDecimal.ZERO)
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

    /**
     * Initiates Mobile Money collection via XentriPay and returns instructions and refid.
     * Strictly creates a PENDING transaction BEFORE calling provider.
     * NEVER credits wallet upon initiation.
     */
    @Transactional
    public MomoTopUpInitResponse initiateMomoTopUp(MomoTopUpRequest request) {
        Wallet wallet = getOrCreateWallet();
        User currentUser = userService.getCurrentUser();

        // 1. Idempotency Check: prevent duplicate payments on repeated taps or network retries
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            var existingTx = transactionRepository.findByReference(idempotencyKey.trim());
            if (existingTx.isPresent()) {
                Transaction tx = existingTx.get();
                log.info("Idempotency match found for reference {}: status={}", idempotencyKey, tx.getStatus());
                WalletResponse wResp = mapToWalletResponse(wallet);
                return MomoTopUpInitResponse.builder()
                        .id(wResp.getId())
                        .userId(wResp.getUserId())
                        .userName(wResp.getUserName())
                        .balance(wResp.getBalance())
                        .heldBalance(wResp.getHeldBalance())
                        .hasPinSet(wResp.isHasPinSet())
                        .success(true)
                        .refid(tx.getReference())
                        .tid(tx.getExternalReference())
                        .instructions("Existing payment request active. Please authorize on your phone or check pending on *182*7*1#.")
                        .status(tx.getStatus() == TransactionStatus.COMPLETED ? "COMPLETED" : "PENDING")
                        .wallet(wResp)
                        .transaction(mapToTransactionResponse(tx))
                        .build();
            }
        } else {
            idempotencyKey = "TOPUP-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }

        String providerName = request.getProvider() != null ? request.getProvider().toUpperCase() : "MTN";

        // 2. Create the PENDING transaction record BEFORE calling provider
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .description("MoMo Top-Up via " + providerName + " (" + request.getPhoneNumber() + ") - Awaiting authorization")
                .reference(idempotencyKey)
                .provider(providerName)
                .phoneNumber(request.getPhoneNumber())
                .currency("RWF")
                .beforeBalance(wallet.getBalance())
                .createdAt(LocalDateTime.now())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        // 3. Initiate payment with XentriPay
        MomoGatewayService.MomoTransactionResponse momoRes = momoGatewayService.initiatePayment(
                currentUser.getEmail(),
                currentUser.getFullName(),
                request.getPhoneNumber(),
                request.getAmount(),
                providerName
        );

        // 4. Update transaction with provider reference if returned
        if (momoRes.getTransactionId() != null && !momoRes.getTransactionId().isEmpty()) {
            savedTx.setReference(momoRes.getTransactionId());
        }
        if (momoRes.getExternalRef() != null) {
            savedTx.setExternalReference(momoRes.getExternalRef());
        }
        savedTx.setDescription("MoMo Top-Up via " + providerName + " (" + request.getPhoneNumber() + ") - " + momoRes.getMessage());
        savedTx = transactionRepository.save(savedTx);

        // 5. DO NOT credit wallet! Balance remains strictly unchanged until provider confirmation.
        WalletResponse walletResp = mapToWalletResponse(wallet);

        return MomoTopUpInitResponse.builder()
                .id(walletResp.getId())
                .userId(walletResp.getUserId())
                .userName(walletResp.getUserName())
                .balance(walletResp.getBalance())
                .heldBalance(walletResp.getHeldBalance())
                .hasPinSet(walletResp.isHasPinSet())
                .success(true)
                .refid(savedTx.getReference())
                .tid(savedTx.getExternalReference())
                .instructions(momoRes.getMessage() != null ? momoRes.getMessage() : "Please authorize payment on your mobile phone or dial *182*7*1#.")
                .status("PENDING")
                .wallet(walletResp)
                .transaction(mapToTransactionResponse(savedTx))
                .build();
    }

    /** Top up wallet with Mobile Money (returns WalletResponse for backward compatibility) */
    @Transactional
    public WalletResponse topUpWithMomo(MomoTopUpRequest request) {
        MomoTopUpInitResponse initResp = initiateMomoTopUp(request);
        return initResp.getWallet();
    }

    /**
     * Checks status of a pending collection using its refid.
     * ONLY credits the wallet balance if XentriPay confirms final SUCCESS/SETTLED state.
     * Atomic, pessimistic write-locking prevents double-crediting.
     */
    @Transactional
    public CollectionVerificationResponse verifyCollectionStatus(String refid) {
        Transaction transaction = transactionRepository.findByReference(refid)
                .or(() -> transactionRepository.findByExternalReference(refid))
                .orElseThrow(() -> new AppException("Transaction with reference " + refid + " not found", HttpStatus.NOT_FOUND));

        Wallet wallet = transaction.getWallet();

        // 1. If already COMPLETED, return current settled state (idempotent)
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return buildVerificationResponse(wallet, transaction, "COMPLETED", "Payment previously verified and credited successfully.");
        }

        // 2. If already FAILED, return failed state
        if (transaction.getStatus() == TransactionStatus.FAILED) {
            return buildVerificationResponse(wallet, transaction, "FAILED", "Payment previously failed or was cancelled.");
        }

        // 3. Query provider status
        XentripayCollectionStatusResponse statusResp = xentripayService.checkCollectionStatus(refid);
        String providerStatus = statusResp != null ? statusResp.getStatus() : "PENDING";

        if ("SUCCESS".equalsIgnoreCase(providerStatus) || "COMPLETED".equalsIgnoreCase(providerStatus) || "SETTLED".equalsIgnoreCase(providerStatus)) {
            // Row-level locking to guarantee atomic credit and prevent double-crediting
            Wallet lockedWallet = walletRepository.findByIdForUpdate(wallet.getId()).orElse(wallet);

            // Double check in case concurrent thread already completed
            if (transaction.getStatus() == TransactionStatus.COMPLETED) {
                return buildVerificationResponse(lockedWallet, transaction, "COMPLETED", "Payment confirmed and credited.");
            }

            BigDecimal beforeBalance = lockedWallet.getBalance();
            BigDecimal afterBalance = beforeBalance.add(transaction.getAmount());

            lockedWallet.setBalance(afterBalance);
            lockedWallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(lockedWallet);

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transaction.setBeforeBalance(beforeBalance);
            transaction.setAfterBalance(afterBalance);
            transaction.setDescription("MoMo Top-Up via " + transaction.getProvider() + " (" + transaction.getPhoneNumber() +
                    ") - Confirmed. Balance: " + beforeBalance + " -> " + afterBalance + " RWF");
            transactionRepository.save(transaction);

            log.info("Collection {} confirmed! Credited {} RWF (balance {} -> {}) for user {}",
                    refid, transaction.getAmount(), beforeBalance, afterBalance, lockedWallet.getUser().getId());

            return buildVerificationResponse(lockedWallet, transaction, "COMPLETED", "Payment successful! Wallet credited.");
        } else if ("FAILED".equalsIgnoreCase(providerStatus) || "CANCELLED".equalsIgnoreCase(providerStatus) || "DECLINED".equalsIgnoreCase(providerStatus)) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            log.info("Collection {} failed/cancelled by provider/user.", refid);
            return buildVerificationResponse(wallet, transaction, "FAILED", "Payment failed or was cancelled on your phone.");
        }

        // 4. Still PENDING
        return buildVerificationResponse(wallet, transaction, "PENDING", "Payment awaiting authorization. Please confirm on your mobile phone or dial *182*7*1#.");
    }

    private CollectionVerificationResponse buildVerificationResponse(Wallet wallet, Transaction tx, String status, String message) {
        WalletResponse wResp = mapToWalletResponse(wallet);
        return CollectionVerificationResponse.builder()
                .id(wResp.getId())
                .userId(wResp.getUserId())
                .userName(wResp.getUserName())
                .balance(wResp.getBalance())
                .heldBalance(wResp.getHeldBalance())
                .hasPinSet(wResp.isHasPinSet())
                .status(status)
                .message(message)
                .reference(tx.getReference())
                .externalReference(tx.getExternalReference())
                .amount(tx.getAmount())
                .beforeBalance(tx.getBeforeBalance())
                .afterBalance(tx.getAfterBalance())
                .wallet(wResp)
                .transaction(mapToTransactionResponse(tx))
                .build();
    }

    /**
     * Handles inbound webhooks from XentriPay or other payment gateways
     */
    @Transactional
    public CollectionVerificationResponse processWebhook(XentripayWebhookPayload payload) {
        if (payload == null || (payload.getRefid() == null && payload.getTid() == null)) {
            throw new AppException("Invalid webhook payload", HttpStatus.BAD_REQUEST);
        }

        String ref = payload.getRefid() != null ? payload.getRefid() : payload.getTid();
        log.info("Received XentriPay webhook for ref={}, status={}", ref, payload.getStatus());

        return verifyCollectionStatus(ref);
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
        wallet.setHeldBalance((wallet.getHeldBalance() != null ? wallet.getHeldBalance() : BigDecimal.ZERO).add(amount));

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
        BigDecimal currentHeld = wallet.getHeldBalance() != null ? wallet.getHeldBalance() : BigDecimal.ZERO;
        wallet.setHeldBalance(currentHeld.subtract(transaction.getAmount()).max(BigDecimal.ZERO));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Record refund transaction
        Transaction refundTransaction = Transaction.builder()
                .wallet(wallet)
                .amount(transaction.getAmount())
                .type(TransactionType.BID_REFUND)
                .status(TransactionStatus.COMPLETED)
                .description("Refund for outbid bid on " +
                        (transaction.getProduct() != null ? transaction.getProduct().getName() : "Auction"))
                .product(transaction.getProduct())
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(refundTransaction);

        return mapToWalletResponse(wallet);
    }

    /** Deduct funds using DTO request */
    @Transactional
    public WalletResponse deductFunds(DeductRequest request) {
        return deductFunds(request.getAmount(), request.getDescription(), request.getPin());
    }

    /** Deduct funds from wallet with optional PIN check */
    @Transactional
    public WalletResponse deductFunds(BigDecimal amount, String description, String pin) {
        Wallet wallet = getOrCreateWallet();

        // Check if PIN matches if wallet has a PIN set
        if (wallet.getPin() != null && !wallet.getPin().isEmpty()) {
            if (pin == null || !wallet.getPin().equals(pin)) {
                throw new AppException("Invalid wallet PIN", HttpStatus.BAD_REQUEST);
            }
        }

        // Check balance
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException("Insufficient wallet balance ($" + amount + " required, current balance: $" + wallet.getBalance() + ")", HttpStatus.BAD_REQUEST);
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

    /** Withdraw funds to Mobile Money or Bank using XentriPay Payouts */
    @Transactional
    public WalletResponse withdraw(WithdrawRequest request) {
        Wallet wallet = getOrCreateWallet();
        User currentUser = wallet.getUser();

        // Check PIN if set
        if (wallet.getPin() != null && !wallet.getPin().isEmpty()) {
            if (request.getPin() == null || !wallet.getPin().equals(request.getPin())) {
                throw new AppException("Invalid wallet PIN", HttpStatus.BAD_REQUEST);
            }
        }

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new AppException("Insufficient wallet balance for withdrawal", HttpStatus.BAD_REQUEST);
        }

        // Deduct funds immediately to prevent double spending
        wallet.setBalance(wallet.getBalance().subtract(request.getAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        String provider = (request.getProvider() != null && !request.getProvider().isEmpty()) ? request.getProvider() : "MTN";
        String customerRef = "TXN-WDRW-" + System.currentTimeMillis() + "-" + currentUser.getId();

        MomoGatewayService.MomoTransactionResponse disbursement = momoGatewayService.initiateDisbursement(
                customerRef,
                request.getDestination(),
                request.getAmount(),
                provider,
                currentUser.getFullName()
        );

        if ("FAILED".equalsIgnoreCase(disbursement.getStatus())) {
            // Rollback wallet balance if gateway rejected the payout immediately
            wallet.setBalance(wallet.getBalance().add(request.getAmount()));
            walletRepository.save(wallet);
            throw new AppException("Withdrawal rejected: " + disbursement.getMessage(), HttpStatus.BAD_GATEWAY);
        }

        boolean isCompleted = "SUCCESSFUL".equalsIgnoreCase(disbursement.getStatus()) || "COMPLETED".equalsIgnoreCase(disbursement.getStatus());

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .type(TransactionType.WITHDRAWAL)
                .status(isCompleted ? TransactionStatus.COMPLETED : TransactionStatus.PENDING)
                .description("Withdrawal to " + provider + " (" + request.getDestination() + ") - " + disbursement.getMessage())
                .reference(customerRef)
                .externalReference(disbursement.getExternalRef())
                .provider(provider)
                .phoneNumber(request.getDestination())
                .currency("RWF")
                .createdAt(LocalDateTime.now())
                .completedAt(isCompleted ? LocalDateTime.now() : null)
                .build();

        transactionRepository.save(transaction);
        return mapToWalletResponse(wallet);
    }

    /**
     * Checks status of a payout request. If failed or reversed, refunds wallet.
     */
    @Transactional
    public TransactionResponse checkPayoutStatus(String customerRef) {
        Transaction transaction = transactionRepository.findByReference(customerRef)
                .orElseThrow(() -> new AppException("Transaction not found for reference " + customerRef, HttpStatus.NOT_FOUND));

        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return mapToTransactionResponse(transaction);
        }

        XentripayPayoutStatusResponse statusResp = xentripayService.checkPayoutStatus(customerRef);
        if (statusResp != null && statusResp.getData() != null) {
            String remoteStatus = statusResp.getData().getStatus();
            if ("COMPLETED".equalsIgnoreCase(remoteStatus) || "SUCCESSFUL".equalsIgnoreCase(remoteStatus)) {
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
            } else if ("FAILED".equalsIgnoreCase(remoteStatus) || "REVERSED".equalsIgnoreCase(remoteStatus)) {
                // Refund wallet balance
                Wallet wallet = transaction.getWallet();
                wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
                wallet.setUpdatedAt(LocalDateTime.now());
                walletRepository.save(wallet);

                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setDescription(transaction.getDescription() + " (Failed: Refunded)");
                transactionRepository.save(transaction);
            }
        }

        return mapToTransactionResponse(transaction);
    }

    /** Validate account holder name at bank or mobile money operator */
    public ValidateAccountResponse validateAccount(ValidateAccountRequest request) {
        PaymentProvider provider = PaymentProvider.find(request.getProvider())
                .orElse(PaymentProvider.MTN_MOMO);

        XentripayValidateNameResponse resp = xentripayService.validateAccountName(
                request.getAccountNumber(), provider.getId()
        );

        return ValidateAccountResponse.builder()
                .accountNumber(request.getAccountNumber())
                .providerId(provider.getId())
                .providerName(provider.getDisplayName())
                .registeredName(resp != null ? resp.getRegisteredName() : null)
                .valid(resp != null && resp.getRegisteredName() != null && !resp.getRegisteredName().isEmpty())
                .build();
    }

    /** Returns list of supported banks and mobile money operators */
    public List<ProviderInfoResponse> getSupportedProviders() {
        return java.util.Arrays.stream(PaymentProvider.values())
                .map(p -> ProviderInfoResponse.builder()
                        .id(p.getId())
                        .code(p.name())
                        .name(p.getOfficialName())
                        .displayName(p.getDisplayName())
                        .isMobileMoney(p.isMobileMoney())
                        .build())
                .collect(Collectors.toList());
    }

    /** Checks Centrika XentriPay merchant balance */
    public XentripayBusinessWalletResponse getBusinessBalance() {
        return xentripayService.getBusinessWalletBalance();
    }

    /** P2P Transfer funds to another user */
    @Transactional
    public WalletResponse transfer(TransferRequest request) {
        Wallet senderWallet = getOrCreateWallet();
        User sender = senderWallet.getUser();

        // Check PIN
        if (senderWallet.getPin() != null && !senderWallet.getPin().isEmpty()) {
            if (request.getPin() == null || !senderWallet.getPin().equals(request.getPin())) {
                throw new AppException("Invalid wallet PIN", HttpStatus.BAD_REQUEST);
            }
        }

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new AppException("Insufficient wallet balance for transfer", HttpStatus.BAD_REQUEST);
        }

        String target = request.getRecipient().trim();
        if (target.startsWith("@")) {
            target = target.substring(1);
        }
        final String searchTarget = target;

        User recipient = userRepository.findByUsername(searchTarget)
                .or(() -> userRepository.findByEmail(searchTarget))
                .orElseThrow(() -> new AppException("Recipient user @" + searchTarget + " not found", HttpStatus.NOT_FOUND));

        if (recipient.getId().equals(sender.getId())) {
            throw new AppException("You cannot transfer funds to yourself", HttpStatus.BAD_REQUEST);
        }

        Wallet recipientWallet = walletRepository.findByUser(recipient)
                .orElseGet(() -> walletRepository.save(Wallet.builder().user(recipient).balance(BigDecimal.ZERO).heldBalance(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build()));

        // Deduct sender
        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        senderWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(senderWallet);

        // Credit recipient
        recipientWallet.setBalance(recipientWallet.getBalance().add(request.getAmount()));
        recipientWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(recipientWallet);

        // Record sender transaction
        String noteDesc = request.getNote() != null && !request.getNote().isEmpty() ? " — " + request.getNote() : "";
        Transaction senderTx = Transaction.builder()
                .wallet(senderWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.COMPLETED)
                .description("Transfer to @" + recipient.getUsername() + noteDesc)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(senderTx);

        // Record recipient transaction
        Transaction recipientTx = Transaction.builder()
                .wallet(recipientWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.COMPLETED)
                .description("Transfer from @" + sender.getUsername() + noteDesc)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(recipientTx);

        return mapToWalletResponse(senderWallet);
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
        BigDecimal currentHeld = wallet.getHeldBalance() != null ? wallet.getHeldBalance() : BigDecimal.ZERO;
        wallet.setHeldBalance(currentHeld.subtract(amount).max(BigDecimal.ZERO));
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

    @Transactional
    public void completeBidPayout(Long winnerUserId, Long sellerUserId, BigDecimal amount, Long productId) {
        Wallet winnerWallet = walletRepository.findByUserId(winnerUserId)
                .orElseThrow(() -> new AppException("Winner wallet not found", HttpStatus.NOT_FOUND));
        
        // Deduct winner's held balance
        BigDecimal currentHeld = winnerWallet.getHeldBalance() != null ? winnerWallet.getHeldBalance() : BigDecimal.ZERO;
        winnerWallet.setHeldBalance(currentHeld.subtract(amount).max(BigDecimal.ZERO));
        walletRepository.save(winnerWallet);

        // Find the product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        // Credit creator wallet minus 5% platform commission
        BigDecimal platformCommission = amount.multiply(new BigDecimal("0.05"));
        BigDecimal sellerPayout = amount.subtract(platformCommission);

        Wallet sellerWallet = walletRepository.findByUserId(sellerUserId)
                .orElseGet(() -> {
                    User seller = product.getSeller();
                    Wallet newWallet = Wallet.builder()
                            .user(seller)
                            .balance(BigDecimal.ZERO)
                            .heldBalance(BigDecimal.ZERO)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return walletRepository.save(newWallet);
                });

        sellerWallet.setBalance(sellerWallet.getBalance().add(sellerPayout));
        walletRepository.save(sellerWallet);

        // Create deposit transaction for seller
        Transaction sellerTx = Transaction.builder()
                .wallet(sellerWallet)
                .amount(sellerPayout)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description("Payout for won auction: " + product.getName() + " (minus 5% platform fee)")
                .product(product)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(sellerTx);
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

    /** Verify user PIN */
    @Transactional(readOnly = true)
    public boolean verifyPin(String pin) {
        Wallet wallet = getOrCreateWallet();
        if (wallet.getPin() == null || wallet.getPin().isEmpty()) {
            return true;
        }
        return wallet.getPin().equals(pin);
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
                .heldBalance(wallet.getHeldBalance() != null ? wallet.getHeldBalance() : BigDecimal.ZERO)
                .hasPinSet(wallet.getPin() != null && !wallet.getPin().isEmpty())
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
                .reference(transaction.getReference())
                .externalReference(transaction.getExternalReference())
                .provider(transaction.getProvider())
                .phoneNumber(transaction.getPhoneNumber())
                .currency(transaction.getCurrency())
                .beforeBalance(transaction.getBeforeBalance())
                .afterBalance(transaction.getAfterBalance())
                .productId(transaction.getProduct() != null ? transaction.getProduct().getId() : null)
                .productName(transaction.getProduct() != null ? transaction.getProduct().getName() : null)
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }

    /**
     * Automated background scheduler: Reconciles pending deposits and payouts every 30 seconds.
     * Ensures users receive their funds even if they approve USSD after closing the app.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    @Transactional
    public void reconcilePendingTransactions() {
        // Reconcile pending DEPOSITS (Collections)
        List<Transaction> pendingDeposits = transactionRepository.findByStatusAndTypeAndReferenceIsNotNull(
                TransactionStatus.PENDING, TransactionType.DEPOSIT);

        for (Transaction tx : pendingDeposits) {
            try {
                if (tx.getCreatedAt() != null && tx.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
                    XentripayCollectionStatusResponse status = xentripayService.checkCollectionStatus(tx.getReference());
                    if (status != null && ("SUCCESS".equalsIgnoreCase(status.getStatus()) || "COMPLETED".equalsIgnoreCase(status.getStatus()) || "SETTLED".equalsIgnoreCase(status.getStatus()))) {
                        Wallet lockedWallet = walletRepository.findByIdForUpdate(tx.getWallet().getId()).orElse(tx.getWallet());
                        if (tx.getStatus() != TransactionStatus.COMPLETED) {
                            BigDecimal before = lockedWallet.getBalance();
                            BigDecimal after = before.add(tx.getAmount());
                            lockedWallet.setBalance(after);
                            lockedWallet.setUpdatedAt(LocalDateTime.now());
                            walletRepository.save(lockedWallet);

                            tx.setStatus(TransactionStatus.COMPLETED);
                            tx.setCompletedAt(LocalDateTime.now());
                            tx.setBeforeBalance(before);
                            tx.setAfterBalance(after);
                            tx.setDescription("MoMo Top-Up via " + tx.getProvider() + " (" + tx.getPhoneNumber() +
                                    ") - Reconciled & Confirmed. Balance: " + before + " -> " + after + " RWF");
                            transactionRepository.save(tx);
                            log.info("Reconciled pending deposit tx ID {} (ref={}): Credited {} RWF (balance {} -> {})",
                                    tx.getId(), tx.getReference(), tx.getAmount(), before, after);
                        }
                    } else if (status != null && ("FAILED".equalsIgnoreCase(status.getStatus()) || "CANCELLED".equalsIgnoreCase(status.getStatus()) || "DECLINED".equalsIgnoreCase(status.getStatus()))) {
                        tx.setStatus(TransactionStatus.FAILED);
                        tx.setCompletedAt(LocalDateTime.now());
                        transactionRepository.save(tx);
                    }
                }
            } catch (Exception e) {
                log.debug("Reconciliation skipped for deposit ref {}: {}", tx.getReference(), e.getMessage());
            }
        }

        // Reconcile pending WITHDRAWALS (Payouts)
        List<Transaction> pendingWithdrawals = transactionRepository.findByStatusAndTypeAndReferenceIsNotNull(
                TransactionStatus.PENDING, TransactionType.WITHDRAWAL);

        for (Transaction tx : pendingWithdrawals) {
            try {
                if (tx.getCreatedAt() != null && tx.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
                    XentripayPayoutStatusResponse payoutStatus = xentripayService.checkPayoutStatus(tx.getReference());
                    if (payoutStatus != null && payoutStatus.getData() != null) {
                        String st = payoutStatus.getData().getStatus();
                        if ("COMPLETED".equalsIgnoreCase(st) || "SUCCESSFUL".equalsIgnoreCase(st)) {
                            tx.setStatus(TransactionStatus.COMPLETED);
                            tx.setCompletedAt(LocalDateTime.now());
                            transactionRepository.save(tx);
                            log.info("Reconciled pending withdrawal tx ID {} (ref={}): Marked COMPLETED", tx.getId(), tx.getReference());
                        } else if ("FAILED".equalsIgnoreCase(st) || "REVERSED".equalsIgnoreCase(st)) {
                            Wallet wallet = tx.getWallet();
                            wallet.setBalance(wallet.getBalance().add(tx.getAmount()));
                            wallet.setUpdatedAt(LocalDateTime.now());
                            walletRepository.save(wallet);

                            tx.setStatus(TransactionStatus.FAILED);
                            tx.setCompletedAt(LocalDateTime.now());
                            transactionRepository.save(tx);
                            log.warn("Reconciled failed withdrawal tx ID {} (ref={}): Refunded {} RWF to user", tx.getId(), tx.getReference(), tx.getAmount());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Reconciliation skipped for payout ref {}: {}", tx.getReference(), e.getMessage());
            }
        }
    }
}
