package com.celebstash.backend.repository;

import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUser(User user);
    Optional<Wallet> findByUserId(Long userId);
}
