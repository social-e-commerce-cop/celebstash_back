package com.celebstash.backend.config;

import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ProductRepository productRepository;
    private final PostRepository postRepository;
    private final StoryRepository storyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            log.info("Database already seeded with initial data.");
            return;
        }

        log.info("Seeding initial demo data for CelebStash Standard User and Artist...");

        // 1. Create Default Standard User
        User defaultUser = User.builder()
                .fullName("Ange (Standard User)")
                .email("user@zikiii.com")
                .phoneNumber("+1234567890")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(true)
                .build();

        defaultUser = userRepository.save(defaultUser);

        // Wallet for Standard User
        Wallet userWallet = Wallet.builder()
                .user(defaultUser)
                .balance(new BigDecimal("1250.50"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        walletRepository.save(userWallet);

        // 2. Create Demo Artist User
        User artistUser = User.builder()
                .fullName("Taylor Swift")
                .email("artist@zikiii.com")
                .phoneNumber("+1987654321")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(true)
                .build();

        artistUser = userRepository.save(artistUser);

        // Wallet for Artist
        Wallet artistWallet = Wallet.builder()
                .user(artistUser)
                .balance(new BigDecimal("5000.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        walletRepository.save(artistWallet);

        // 3. Create Sample Products
        Product product1 = Product.builder()
                .seller(artistUser)
                .name("Signed Midnights Vinyl - Limited Edition")
                .description("Authentic hand-signed vinyl record from the tour.")
                .price(new BigDecimal("149.99"))
                .stockQuantity(25)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1539185441755-769473a23570?q=80&w=800"
                ))
                .productType(ProductType.REGULAR)
                .status(ProductStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        product1 = productRepository.save(product1);

        Product biddingProduct = Product.builder()
                .seller(artistUser)
                .name("VIP Backstage Pass & Front Row Seat")
                .description("Exclusive auction for 1-on-1 backstage access and front row experience.")
                .price(new BigDecimal("500.00"))
                .initialBidPrice(new BigDecimal("500.00"))
                .currentBidPrice(new BigDecimal("750.00"))
                .currentBidder(defaultUser)
                .stockQuantity(1)
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=800"
                ))
                .productType(ProductType.BIDDING)
                .status(ProductStatus.APPROVED)
                .bidStartTime(LocalDateTime.now().minusHours(2))
                .bidEndTime(LocalDateTime.now().plusDays(3))
                .approvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        biddingProduct = productRepository.save(biddingProduct);

        // 4. Create Sample Feed Posts
        Post post1 = Post.builder()
                .user(artistUser)
                .product(product1)
                .description("Just unboxed the new signed vinyl collection! Available exclusively in the stash shop. 🎶 #Merch #Vinyl")
                .videoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1539185441755-769473a23570?q=80&w=800",
                        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=800",
                        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=800"
                ))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        postRepository.save(post1);

        // 5. Create Sample Active Story
        Story story1 = Story.builder()
                .user(artistUser)
                .product(product1)
                .mediaUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=800")
                .caption("Live backstage soundcheck! 🎤")
                .mediaType("IMAGE")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        storyRepository.save(story1);

        log.info("Demo data successfully initialized!");
        log.info("Default Standard User -> Email: user@zikiii.com | Password: password123");
        log.info("Default Artist User -> Email: artist@zikiii.com | Password: password123");
    }