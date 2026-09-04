package com.celebstash.backend.config;

import com.celebstash.backend.model.User;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.AccountStatus;
import com.celebstash.backend.model.enums.AuthProvider;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.UserRepository;
import com.celebstash.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = "karabogretta@gmail.com";

        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            log.info("Seeding initial Admin user for CelebStash dashboard...");

            User admin = User.builder()
                    .fullName("Admin User")
                    .email(adminEmail)
                    .phoneNumber("+250780000001")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .status(AccountStatus.ACTIVE)
                    .provider(AuthProvider.LOCAL)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build();

            admin = userRepository.save(admin);

            // Create associated wallet for the admin
            Wallet adminWallet = Wallet.builder()
                    .user(admin)
                    .balance(new BigDecimal("10000.00"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            walletRepository.save(adminWallet);

            log.info("==================================================================");
            log.info(">>> Admin user seeded successfully! <<<");
            log.info(">>> Login Email:    {}", adminEmail);
            log.info(">>> Login Password: {}", "admin123");
            log.info("==================================================================");
        } else {
            log.info("Admin user ({}) already exists in database.", adminEmail);
        }
    }
}
