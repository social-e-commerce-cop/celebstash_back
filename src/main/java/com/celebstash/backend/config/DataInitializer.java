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
import java.util.Optional;

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

    /** Identity the admin row is seeded with; also used to find an admin whose email has drifted. */
    private static final String ADMIN_USERNAME = "karabogretta";
    private static final String ADMIN_PHONE = "+1112223333";

    @org.springframework.beans.factory.annotation.Value("${app.seed.admin-email:karabogretta@gmail.com}")
    private String adminEmail;

    @org.springframework.beans.factory.annotation.Value("${app.seed.admin-password:}")
    private String adminPassword;

    @org.springframework.beans.factory.annotation.Value("${app.seed.demo-password:}")
    private String demoPassword;

    /**
     * Resolves a seed password from configuration. Seed passwords are never hardcoded: when no
     * value is configured a random one is generated and logged once, so a fresh deployment can
     * never ship with a credential that is publicly known from the source code.
     */
    private String resolveSeedPassword(String configured, String label) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String generated = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.warn("=================================================================");
        log.warn("No seed password configured for {}. Generated one-time password: {}", label, generated);
        log.warn("Set the matching environment variable to control this credential.");
        log.warn("=================================================================");
        return generated;
    }

    /**
     * Seeding must never prevent the application from serving traffic. A CommandLineRunner that
     * throws aborts the whole Spring Boot process, so a constraint violation while seeding demo
     * data would take a running deployment down. Failures are logged loudly and startup continues.
     */
    @Override
    public void run(String... args) {
        try {
            seed();
        } catch (Exception e) {
            log.error("Data initialization failed; the application will continue to start. Cause: {}",
                    e.getMessage(), e);
        }
    }

    private void seed() {
        // Migration: Ensure all existing user records have a unique username
        migrateExistingUsersUsernames();

        // Drop legacy constraints if present & update nullability
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;");
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;");
            jdbcTemplate.execute("ALTER TABLE posts ALTER COLUMN video_url DROP NOT NULL;");
            jdbcTemplate.execute("ALTER TABLE posts ALTER COLUMN description DROP NOT NULL;");
            // Posts are not required to advertise a product (Post.product is an optional
            // association); legacy tables created before that carry a NOT NULL that
            // hibernate's ddl-auto=update never drops, which blocks all plain posts.
            jdbcTemplate.execute("ALTER TABLE posts ALTER COLUMN product_id DROP NOT NULL;");
            jdbcTemplate.execute("UPDATE music_releases SET availability_status = 'UNRELEASED' WHERE availability_status IS NULL;");
            jdbcTemplate.execute("ALTER TABLE music_releases ALTER COLUMN availability_status DROP NOT NULL;");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS stored_files (id BIGSERIAL PRIMARY KEY, file_name VARCHAR(255) NOT NULL UNIQUE, original_file_name VARCHAR(255), content_type VARCHAR(100), size BIGINT, data BYTEA, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_stored_files_name ON stored_files(file_name);");

            // Ensure post_saves and post_reposts tables and constraints exist
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_saves (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uk_post_saves_user_post UNIQUE (user_id, post_id));");
            jdbcTemplate.execute("ALTER TABLE post_saves ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY;");
            jdbcTemplate.execute("ALTER TABLE post_saves ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;");
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_post_saves_user_post ON post_saves(user_id, post_id);");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_reposts (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uk_post_reposts_user_post UNIQUE (user_id, post_id));");
            jdbcTemplate.execute("ALTER TABLE post_reposts ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY;");
            jdbcTemplate.execute("ALTER TABLE post_reposts ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;");
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_post_reposts_user_post ON post_reposts(user_id, post_id);");

            // Direct-to-Fan Music Release schema updates
            jdbcTemplate.execute("ALTER TABLE music_releases ADD COLUMN IF NOT EXISTS community_conversation_id BIGINT;");
            jdbcTemplate.execute("ALTER TABLE music_releases ADD COLUMN IF NOT EXISTS connected_event_id BIGINT;");
            jdbcTemplate.execute("ALTER TABLE music_releases ADD COLUMN IF NOT EXISTS connected_product_ids VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE music_releases ADD COLUMN IF NOT EXISTS download_allowed BOOLEAN DEFAULT TRUE;");
            jdbcTemplate.execute("ALTER TABLE music_releases ADD COLUMN IF NOT EXISTS early_access_date TIMESTAMP;");

            jdbcTemplate.execute("ALTER TABLE music_tracks ADD COLUMN IF NOT EXISTS is_bonus_track BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE music_tracks ADD COLUMN IF NOT EXISTS preview_duration_seconds INT DEFAULT 5;");
            jdbcTemplate.execute("ALTER TABLE music_tracks ADD COLUMN IF NOT EXISTS allow_download BOOLEAN DEFAULT TRUE;");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS release_benefits (id BIGSERIAL PRIMARY KEY, release_id BIGINT NOT NULL REFERENCES music_releases(id) ON DELETE CASCADE, benefit_type VARCHAR(100) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, custom_name VARCHAR(255), custom_description VARCHAR(2000), config_data VARCHAR(4000));");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS music_exclusive_contents (id BIGSERIAL PRIMARY KEY, release_id BIGINT NOT NULL REFERENCES music_releases(id) ON DELETE CASCADE, title VARCHAR(255) NOT NULL, description VARCHAR(2000), content_type VARCHAR(100) NOT NULL DEFAULT 'VIDEO', media_url VARCHAR(500) NOT NULL, thumbnail_url VARCHAR(500), sort_order INT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS music_waitlists (id BIGSERIAL PRIMARY KEY, release_id BIGINT NOT NULL REFERENCES music_releases(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, notified BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uk_music_waitlist_user_release UNIQUE (user_id, release_id));");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS music_access (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, release_id BIGINT NOT NULL REFERENCES music_releases(id) ON DELETE CASCADE, transaction_id BIGINT REFERENCES transactions(id), status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, is_gift BOOLEAN DEFAULT FALSE, gifted_by_id BIGINT REFERENCES users(id), gift_message VARCHAR(1000), granted_benefits_snapshot VARCHAR(4000), CONSTRAINT uk_music_access_user_release UNIQUE (user_id, release_id));");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check;");

            log.info("Successfully dropped legacy user constraints, ensured stored_files, post_saves, post_reposts, and music access tables exist.");
        } catch (Exception e) {
            log.warn("Could not drop legacy constraints/alter columns: {}", e.getMessage());
        }

        // Ensure Admin user exists.
        //
        // The admin is located by email, then username, then phone. Matching on email alone is
        // not enough: when app.seed.admin-email differs from the email an existing admin row was
        // created with, the lookup misses and the create path below collides with that row's
        // unique username/phone — which previously aborted startup entirely.
        Optional<User> existingAdmin = userRepository.findByEmail(adminEmail);
        if (existingAdmin.isEmpty()) {
            existingAdmin = userRepository.findByUsername(ADMIN_USERNAME);
        }
        if (existingAdmin.isEmpty()) {
            existingAdmin = userRepository.findByPhoneNumber(ADMIN_PHONE);
        }

        existingAdmin.ifPresentOrElse(
            admin -> {
                admin.setRole(Role.ADMIN);
                admin.setStatus(AccountStatus.ACTIVE);
                admin.setEmailVerified(true);
                if (admin.getProfilePicture() == null || admin.getProfilePicture().isBlank()) {
                    admin.setProfilePicture("https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800");
                }
                userRepository.save(admin);
                log.info("Admin account already present (id={}, email={}); ensured ADMIN/ACTIVE.",
                        admin.getId(), admin.getEmail());
            },
            () -> {
                // Only claim the default username/phone if they are actually free, so seeding a
                // second environment against a shared database cannot violate a unique constraint.
                String username = userRepository.existsByUsername(ADMIN_USERNAME) ? null : ADMIN_USERNAME;
                String phone = userRepository.findByPhoneNumber(ADMIN_PHONE).isPresent() ? null : ADMIN_PHONE;

                User adminUser = User.builder()
                        .fullName("Emmy Gretta")
                        .username(username)
                        .email(adminEmail)
                        .phoneNumber(phone)
                        .password(passwordEncoder.encode(resolveSeedPassword(adminPassword, "admin account " + adminEmail)))
                        .role(Role.ADMIN)
                        .status(AccountStatus.ACTIVE)
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .phoneVerified(true)
                        .profilePicture("https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800")
                        .build();
                adminUser = userRepository.save(adminUser);
                Wallet adminWallet = Wallet.builder()
                        .user(adminUser)
                        .balance(new BigDecimal("10000.00"))
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                walletRepository.save(adminWallet);
                log.info("Seeded Admin User: {}", adminEmail);
            }
        );

        if (userRepository.count() > 1 && productRepository.count() > 0) {
            log.info("Database already seeded with initial data.");
            return;
        }

        log.info("Seeding initial demo data for CelebStash Standard User and Artist...");

        final String demoAccountPassword = resolveSeedPassword(demoPassword, "demo accounts (@zikiii.com)");

        // 1. Create Default Standard User
        User defaultUser = User.builder()
                .fullName("INEZA Gretta")
                .username("ineza_gretta")
                .email("user@zikiii.com")
                .phoneNumber("+1234567890")
                .password(passwordEncoder.encode(demoAccountPassword))
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
                .password(passwordEncoder.encode(demoAccountPassword))
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
                .password(passwordEncoder.encode(demoAccountPassword))
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
                .password(passwordEncoder.encode(demoAccountPassword))
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
                .password(passwordEncoder.encode(demoAccountPassword))
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
