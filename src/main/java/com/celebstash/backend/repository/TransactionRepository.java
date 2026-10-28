package com.celebstash.backend.repository;

import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.Transaction;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.TransactionStatus;
import com.celebstash.backend.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Find all transactions for a wallet
    List<Transaction> findByWallet(Wallet wallet);

    // Find all transactions for a wallet with a specific status
    List<Transaction> findByWalletAndStatus(Wallet wallet, TransactionStatus status);

    // Find all transactions for a wallet with a specific type
    List<Transaction> findByWalletAndType(Wallet wallet, TransactionType type);

    // Find all transactions for a wallet with a specific type and status
    List<Transaction> findByWalletAndTypeAndStatus(Wallet wallet, TransactionType type, TransactionStatus status);

    // Find all transactions for a wallet ordered by creation date (newest first)
    List<Transaction> findByWalletOrderByCreatedAtDesc(Wallet wallet);

    // Find all transactions for a product with a specific type and status
    List<Transaction> findByProductAndTypeAndStatus(Product product, TransactionType type, TransactionStatus status);

    // Find transaction by unique reference ID
    java.util.Optional<Transaction> findByReference(String reference);

    // Find transaction by external reference
    java.util.Optional<Transaction> findByExternalReference(String externalReference);

    // Find pending transactions by status and type that have references for reconciliation
    List<Transaction> findByStatusAndTypeAndReferenceIsNotNull(TransactionStatus status, TransactionType type);

    // Find all pending transactions that have references
    List<Transaction> findByStatusAndReferenceIsNotNull(TransactionStatus status);
}
