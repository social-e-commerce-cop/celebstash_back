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
    private final ArtistApplicationRepository artistApplicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        // Migration: Ensure all existing user records have a unique username
        migrateExistingUsersUsernames();

        // Drop legacy constraints if present & update nullability
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;");
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;");
            jdbcTemplate.execute("ALTER TABLE posts ALTER COLUMN video_url DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE posts ALTER COLUMN description DROP NOT NULL;");
            jdbcTemplate.execute("UPDATE music_releases SET availability_status = 'UNRELEASED' WHERE availability_status IS NULL;");
            jdbcTemplate.execute("ALTER TABLE music_releases ALTER COLUMN availability_status DROP NOT NULL;");
            log.info("Successfully dropped legacy user constraints & updated table columns nullability.");
        } catch (Exception e) {
            log.warn("Could not drop legacy constraints/alter columns: {}", e.getMessage());
        }

        // Ensure Admin user exists
        userRepository.findByEmail("karabogretta@gmail.com").ifPresentOrElse(
            admin -> {
                admin.setRole(Role.ADMIN);
                admin.setStatus(AccountStatus.ACTIVE);
                admin.setEmailVerified(true);
                userRepository.save(admin);
            },
            () -> {
                User adminUser = User.builder()
                        .fullName("Emmy Gretta")
                        .username("karabogretta")
                        .email("karabogretta@gmail.com")
                        .phoneNumber("+1112223333")
                        .password(passwordEncoder.encode("admin123"))
                        .role(Role.ADMIN)
                        .status(AccountStatus.ACTIVE)
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .phoneVerified(true)
                        .build();
                userRepository.save(adminUser);
                log.info("Seeded Admin User: karabogretta@gmail.com");
            }
        );

        if (userRepository.count() > 1 && productRepository.count() > 0) {
            log.info("Database already seeded with initial data.");
            return;
        }

        log.info("Seeding initial demo data for CelebStash Standard User and Artist...");

        // 1. Create Default Standard User
        User defaultUser = User.builder()
                .fullName("INEZA Gretta")
                .username("ineza_gretta")
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

        // 2. Create Demo Artists
        User artistUser = User.builder()
                .fullName("Taylor Swift")
                .username("taylorswift")
                .email("artist@zikiii.com")
                .phoneNumber("+1987654321")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ARTIST)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(true)
                .bio("14-time Grammy winner. Midnights & Eras Tour.")
                .build();
        artistUser = userRepository.save(artistUser);

        User billieUser = User.builder()
                .fullName("Billie Eilish")
                .username("billie_eilish")
                .email("billie@zikiii.com")
                .phoneNumber("+1987654322")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(true)
                .bio("Singer-songwriter. Hit Me Hard and Soft.")
                .build();
        billieUser = userRepository.save(billieUser);

        User drakeUser = User.builder()
                .fullName("Aubrey Drake Graham")
                .username("champagnepapi")
                .email("drake@zikiii.com")
                .phoneNumber("+1987654323")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(true)
                .bio("OVO Sound founder & global recording artist.")
                .build();
        drakeUser = userRepository.save(drakeUser);

        User szaUser = User.builder()
                .fullName("Solána Imani Rowe (SZA)")
                .username("sza")
                .email("sza@zikiii.com")
                .phoneNumber("+1987654324")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ARTIST)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(true)
                .bio("Grammy-winning R&B singer & songwriter.")
                .build();
        szaUser = userRepository.save(szaUser);

        // Wallets for Artists
        walletRepository.save(Wallet.builder().user(artistUser).balance(new BigDecimal("5000.00")).build());
        walletRepository.save(Wallet.builder().user(billieUser).balance(new BigDecimal("3200.00")).build());
        walletRepository.save(Wallet.builder().user(drakeUser).balance(new BigDecimal("15000.00")).build());
        walletRepository.save(Wallet.builder().user(szaUser).balance(new BigDecimal("8400.00")).build());

        // 3. Create Sample Artist Applications
        artistApplicationRepository.save(ArtistApplication.builder()
                .user(billieUser)
                .stageName("Billie Eilish")
                .category("Alternative / Pop")
                .bio("Grammy & Oscar-winning artist applying for official Stash marketplace verification.")
                .socialProofLink("https://instagram.com/billieeilish")
                .status(ApplicationStatus.PENDING)
                .build());

        artistApplicationRepository.save(ArtistApplication.builder()
                .user(drakeUser)
                .stageName("Drake")
                .category("Hip-Hop / Rap")
                .bio("OVO Sound official creator application for drop stash releases.")
                .socialProofLink("https://instagram.com/champagnepapi")
                .status(ApplicationStatus.PENDING)
                .build());

        artistApplicationRepository.save(ArtistApplication.builder()
                .user(szaUser)
                .stageName("SZA")
                .category("R&B / Soul")
                .bio("SOS tour merchandise and exclusive stash drops.")
                .socialProofLink("https://instagram.com/sza")
                .status(ApplicationStatus.APPROVED)
                .build());

        // 4. Create Sample Products
        Product product1 = Product.builder()
                .seller(artistUser)
                .name("Signed Midnights Vinyl - Limited Edition")
                .description("Authentic hand-signed vinyl record from the tour.")
                .price(new BigDecimal("149.99"))
                .stockQuantity(25)
                .imageUrls(List.of("https://images.unsplash.com/photo-1539185441755-769473a23570?q=80&w=800"))
                .productType(ProductType.REGULAR)
                .status(ProductStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(product1);

        Product pendingProduct1 = Product.builder()
                .seller(drakeUser)
                .name("OVO Tour Vintage Leather Jacket")
                .description("Heavyweight custom-embroidered OVO tour leather jacket. Drop limit: 50.")
                .price(new BigDecimal("450.00"))
                .stockQuantity(50)
                .imageUrls(List.of("https://images.unsplash.com/photo-1551028719-00167b16eac5?q=80&w=800"))
                .productType(ProductType.REGULAR)
                .status(ProductStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(pendingProduct1);

        Product pendingProduct2 = Product.builder()
                .seller(billieUser)
                .name("Hit Me Hard and Soft Oversized Hoodie")
                .description("Eco-friendly organic cotton oversized hoodie from the official album line.")
                .price(new BigDecimal("120.00"))
                .stockQuantity(100)
                .imageUrls(List.of("https://images.unsplash.com/photo-1556905055-8f358a7a47b2?q=80&w=800"))
                .productType(ProductType.REGULAR)
                .status(ProductStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(pendingProduct2);

        Product biddingProduct = Product.builder()
                .seller(artistUser)
                .name("VIP Backstage Pass & Front Row Seat")
                .description("Exclusive auction for 1-on-1 backstage access and front row experience.")
                .price(new BigDecimal("500.00"))
                .initialBidPrice(new BigDecimal("500.00"))
                .currentBidPrice(new BigDecimal("750.00"))
                .currentBidder(defaultUser)
                .stockQuantity(1)
                .imageUrls(List.of("https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=800"))
                .productType(ProductType.BIDDING)
                .status(ProductStatus.APPROVED)
                .bidStartTime(LocalDateTime.now().minusHours(2))
                .bidEndTime(LocalDateTime.now().plusDays(3))
                .approvedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(biddingProduct);

        // 5. Create Sample Feed Posts & Stories
        Post post1 = Post.builder()
                .user(artistUser)
                .product(product1)
                .description("Just unboxed the new signed vinyl collection! Available exclusively in the stash shop. 🎶 #Merch #Vinyl")
                .videoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                .imageUrls(List.of(
                        "https://images.unsplash.com/photo-1539185441755-769473a23570?q=80&w=800"
                ))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        postRepository.save(post1);

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
    }

    private void migrateExistingUsersUsernames() {
        log.info("Migrating existing user accounts to ensure every user has a clean, unique username...");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getUsername() == null || user.getUsername().isBlank()) {
                String baseName = "user";
                if (user.getFullName() != null && !user.getFullName().isBlank()) {
                    baseName = user.getFullName().toLowerCase().replaceAll("[^a-z0-9_]", "");
                } else if (user.getEmail() != null && user.getEmail().contains("@")) {
                    baseName = user.getEmail().split("@")[0].toLowerCase().replaceAll("[^a-z0-9_]", "");
                }
                if (baseName.isBlank()) {
                    baseName = "user";
                }

                String candidate = baseName;
                int suffix = 1;
                while (userRepository.existsByUsername(candidate)) {
                    candidate = baseName + suffix;
                    suffix++;
                }

                user.setUsername(candidate);
                userRepository.save(user);
                log.info("Assigned username '{}' to existing user ID: {} ({})", candidate, user.getId(), user.getEmail());
            }
        }
    }
}