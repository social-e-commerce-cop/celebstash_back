package com.celebstash.backend.controller;

import com.celebstash.backend.dto.wallet.*;
import com.celebstash.backend.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet management APIs")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @Operation(summary = "Get wallet information", description = "Returns the current user's wallet information")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WalletResponse> getWalletInfo() {
        return ResponseEntity.ok(walletService.getWalletInfo());
    }

    @PostMapping("/top-up")
    @Operation(summary = "Top up wallet", description = "Adds funds to the user's wallet")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WalletResponse> topUpWallet(@Valid @RequestBody TopUpRequest request) {
        return ResponseEntity.ok(walletService.topUpWallet(request));
    }

    @PostMapping("/top-up/momo")
    @Operation(summary = "Top up wallet using Mobile Money", description = "Initiates Rwanda MTN MoMo/Airtel Money payment collection via XentriPay")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<com.celebstash.backend.dto.wallet.xentripay.MomoTopUpInitResponse> topUpWithMomo(@Valid @RequestBody MomoTopUpRequest request) {
        return ResponseEntity.ok(walletService.initiateMomoTopUp(request));
    }

    @GetMapping("/collections/status/{refid}")
    @Operation(summary = "Check and verify collection status", description = "Verifies collection status on XentriPay with refid and credits wallet upon success")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<com.celebstash.backend.dto.wallet.xentripay.CollectionVerificationResponse> checkCollectionStatus(@PathVariable String refid) {
        return ResponseEntity.ok(walletService.verifyCollectionStatus(refid));
    }

    @PostMapping("/collections/status/{refid}/verify")
    @Operation(summary = "Verify collection payment", description = "Explicitly verifies whether the pending MoMo top-up prompt was approved")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<com.celebstash.backend.dto.wallet.xentripay.CollectionVerificationResponse> verifyCollectionStatusPost(@PathVariable String refid) {
        return ResponseEntity.ok(walletService.verifyCollectionStatus(refid));
    }

    @PostMapping("/webhook/xentripay")
    @Operation(summary = "XentriPay Payment Webhook", description = "Receives asynchronous payment and collection updates from Centrika XentriPay")
    public ResponseEntity<com.celebstash.backend.dto.wallet.xentripay.CollectionVerificationResponse> handleXentripayWebhook(
            @RequestBody com.celebstash.backend.dto.wallet.xentripay.XentripayWebhookPayload payload) {
        return ResponseEntity.ok(walletService.processWebhook(payload));
    }

    @PostMapping("/deduct")
    @Operation(summary = "Deduct wallet funds", description = "Deducts funds from the user's wallet with optional PIN verification")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WalletResponse> deductFunds(@Valid @RequestBody DeductRequest request) {
        return ResponseEntity.ok(walletService.deductFunds(request));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraws funds from wallet to Mobile Money or bank destination via XentriPay Payouts")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WalletResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(walletService.withdraw(request));
    }

    @GetMapping("/payouts/status/{customerRef}")
    @Operation(summary = "Check payout status", description = "Checks disbursement status on XentriPay using customer reference")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TransactionResponse> checkPayoutStatus(@PathVariable String customerRef) {
        return ResponseEntity.ok(walletService.checkPayoutStatus(customerRef));
    }

    @PostMapping("/validate-account")
    @Operation(summary = "Validate account name", description = "Validates the registered name for a phone or bank account number via XentriPay")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<com.celebstash.backend.dto.wallet.xentripay.ValidateAccountResponse> validateAccount(
            @Valid @RequestBody com.celebstash.backend.dto.wallet.xentripay.ValidateAccountRequest request) {
        return ResponseEntity.ok(walletService.validateAccount(request));
    }

    @GetMapping("/providers")
    @Operation(summary = "Get supported payout providers", description = "Lists all supported Rwandan banks and mobile money operators with their IDs")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<com.celebstash.backend.dto.wallet.xentripay.ProviderInfoResponse>> getSupportedProviders() {
        return ResponseEntity.ok(walletService.getSupportedProviders());
    }

    @GetMapping("/merchant-balance")
    @Operation(summary = "Get XentriPay merchant balance", description = "Retrieves the business account wallet balance from Centrika XentriPay")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<com.celebstash.backend.dto.wallet.xentripay.XentripayBusinessWalletResponse> getMerchantBalance() {
        return ResponseEntity.ok(walletService.getBusinessBalance());
    }

    @PostMapping("/transfer")
    @Operation(summary = "P2P transfer", description = "Transfers funds directly to another user via @username or email")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WalletResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(walletService.transfer(request));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get transaction history", description = "Returns the user's transaction history")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory() {
        return ResponseEntity.ok(walletService.getTransactionHistory());
    }

    @PostMapping("/pin")
    @Operation(summary = "Set wallet PIN", description = "Sets or updates the PIN for the user's wallet")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WalletResponse> setPin(@Valid @RequestBody SetPinRequest request) {
        return ResponseEntity.ok(walletService.setPin(request.getPin()));
    }

    @GetMapping("/pin/status")
    @Operation(summary = "Check PIN status", description = "Checks if the user's wallet has a PIN set")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Boolean>> hasPinSet() {
        boolean hasPinSet = walletService.hasPinSet();
        return ResponseEntity.ok(Map.of("hasPinSet", hasPinSet));
    }

    @PostMapping("/pin/verify")
    @Operation(summary = "Verify PIN", description = "Verifies if the supplied PIN is valid for the user's wallet")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Boolean>> verifyPin(@Valid @RequestBody VerifyPinRequest request) {
        boolean valid = walletService.verifyPin(request.getPin());
        return ResponseEntity.ok(Map.of("valid", valid));
    }
}
