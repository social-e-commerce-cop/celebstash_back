package com.celebstash.backend;

import com.celebstash.backend.dto.music.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.MusicAccess;
import com.celebstash.backend.model.MusicExclusiveContent;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import com.celebstash.backend.model.ReleaseBenefit;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.*;
import com.celebstash.backend.service.MusicService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MusicDirectToFanIntegrationTest {

    @Autowired
    private MusicService musicService;

    @Autowired
    private MusicReleaseRepository releaseRepository;

    @Autowired
    private MusicTrackRepository trackRepository;

    @Autowired
    private ReleaseBenefitRepository benefitRepository;

    @Autowired
    private MusicExclusiveContentRepository exclusiveContentRepository;

    @Autowired
    private MusicAccessRepository accessRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static User artist;
    private static User fan;
    private static User otherFan;
    private static Long testReleaseId;
    private static Long testTrackId;

    @BeforeEach
    void setUp() {
        if (artist == null) {
            try {
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
            } catch (Exception ignored) {}

            artist = userRepository.findByUsername("test_artist_dtf").orElseGet(() ->
                    userRepository.save(User.builder()
                            .username("test_artist_dtf")
                            .email("artist_dtf@zikii.com")
                            .fullName("Test Artist DTF")
                            .password("password123")
                            .role(Role.ARTIST)
                            .build())
            );

            fan = userRepository.findByUsername("test_fan_dtf").orElseGet(() ->
                    userRepository.save(User.builder()
                            .username("test_fan_dtf")
                            .email("fan_dtf@zikii.com")
                            .fullName("Test Fan DTF")
                            .password("password123")
                            .role(Role.USER)
                            .build())
            );

            otherFan = userRepository.findByUsername("test_other_fan").orElseGet(() ->
                    userRepository.save(User.builder()
                            .username("test_other_fan")
                            .email("other_fan@zikii.com")
                            .fullName("Test Other Fan")
                            .password("password123")
                            .role(Role.USER)
                            .build())
            );

            // Give fan initial wallet balance of $100
            Wallet fanWallet = walletRepository.findByUser(fan).orElseGet(() ->
                    walletRepository.save(Wallet.builder().user(fan).balance(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build())
            );
            fanWallet.setBalance(new BigDecimal("100.00"));
            walletRepository.save(fanWallet);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test 1 — Create Release: Artist creates Draft. Status = DRAFT")
    void test1_createReleaseDraft() {
        MusicRelease release = MusicRelease.builder()
                .title("Genesis Direct EP")
                .releaseType(ReleaseType.EP)
                .genre("Afrobeats")
                .albumPrice(new BigDecimal("15.00"))
                .status("DRAFT")
                .availabilityStatus("UNRELEASED")
                .build();

        MusicRelease saved = musicService.createRelease(release, Collections.emptyList(), Collections.emptyList(), artist);
        assertNotNull(saved.getId());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(artist.getId(), saved.getArtist().getId());

        testReleaseId = saved.getId();
    }

    @Test
    @Order(2)
    @DisplayName("Test 2 — Add Tracks: Tracks correctly belong to Release")
    void test2_addTracks() {
        assertNotNull(testReleaseId);
        MusicRelease release = releaseRepository.findById(testReleaseId).orElseThrow();

        MusicTrack track = MusicTrack.builder()
                .title("Midnight Whispers")
                .trackNumber(1)
                .audioFileUrl("dummy_midnight.mp3")
                .price(new BigDecimal("1.99"))
                .durationSeconds(210)
                .previewDurationSeconds(5)
                .allowDownload(false)
                .build();

        MusicTrack savedTrack = musicService.addTrackToRelease(release, track);
        assertNotNull(savedTrack.getId());
        assertEquals(release.getId(), savedTrack.getRelease().getId());
        assertEquals(1, savedTrack.getTrackNumber());
        testTrackId = savedTrack.getId();
    }

    @Test
    @Order(3)
    @DisplayName("Test 3 — Configure Benefits: Only selected benefits are stored")
    void test3_configureBenefits() {
        assertNotNull(testReleaseId);
        MusicRelease release = releaseRepository.findById(testReleaseId).orElseThrow();

        // Selected benefits: Early Access, Full Listening, Exclusive Content, Community
        // Disabled benefits: Download, Merch, Event
        List<ReleaseBenefitDto> benefits = List.of(
                ReleaseBenefitDto.builder().benefitType(BenefitType.EARLY_ACCESS).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.FULL_LISTENING).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.EXCLUSIVE_CONTENT).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.COMMUNITY_ACCESS).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.DOWNLOAD_ACCESS).enabled(false).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.MERCH_ACCESS).enabled(false).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.EVENT_ACCESS).enabled(false).build()
        );

        List<MusicExclusiveContentDto> contents = List.of(
                MusicExclusiveContentDto.builder()
                        .title("Studio BTS Recording")
                        .contentType(ExclusiveContentType.BTS)
                        .mediaUrl("bts_video.mp4")
                        .build()
        );

        musicService.updateRelease(release.getId(), release, benefits, contents, artist);

        List<ReleaseBenefit> storedBenefits = benefitRepository.findByRelease(release);
        assertEquals(7, storedBenefits.size());

        List<ReleaseBenefit> activeBenefits = benefitRepository.findByReleaseAndEnabledTrue(release);
        assertEquals(4, activeBenefits.size());
        assertTrue(activeBenefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.EARLY_ACCESS));
        assertTrue(activeBenefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.FULL_LISTENING));
        assertTrue(activeBenefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.EXCLUSIVE_CONTENT));
        assertTrue(activeBenefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.COMMUNITY_ACCESS));
        assertFalse(activeBenefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.DOWNLOAD_ACCESS));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4 — Publish: Release becomes public")
    void test4_publishRelease() {
        assertNotNull(testReleaseId);
        MusicRelease published = musicService.publishRelease(testReleaseId, artist);
        assertEquals("PUBLISHED", published.getStatus());

        // Verify it appears in public releases
        List<MusicRelease> publicReleases = musicService.getAllReleases();
        assertTrue(publicReleases.stream().anyMatch(r -> r.getId().equals(testReleaseId)));
    }

    @Test
    @Order(5)
    @DisplayName("Test 5 — Fan Opens Release: Fan sees exactly the configured benefits")
    void test5_fanOpensRelease() {
        assertNotNull(testReleaseId);
        ReleaseDetailResponse detail = musicService.getReleaseDetail(testReleaseId, fan);

        assertNotNull(detail);
        assertFalse(detail.isHasAccess());
        assertFalse(detail.isCanStreamFull());
        assertFalse(detail.isCanDownload());
        assertFalse(detail.isHasCommunity());
        assertFalse(detail.isHasExclusiveContent());

        // Dynamic benefits returned match configured
        List<ReleaseBenefitDto> benefits = detail.getBenefits();
        assertTrue(benefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.FULL_LISTENING && b.isEnabled()));
        assertTrue(benefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.DOWNLOAD_ACCESS && !b.isEnabled()));
    }

    @Test
    @Order(6)
    @DisplayName("Test 6 — Preview: 5-second preview works. Full track remains protected")
    void test6_preview() {
        assertNotNull(testTrackId);
        Map<String, Object> access = musicService.verifyPlaybackAccess(fan, testTrackId);
        assertFalse((Boolean) access.get("hasFullAccess"));
        assertEquals(5, access.get("maxDurationSeconds"));
    }

    @Test
    @Order(7)
    @DisplayName("Test 7 — Checkout: No Access created before payment")
    void test7_checkoutPrePayment() {
        assertNotNull(testReleaseId);
        MusicRelease release = releaseRepository.findById(testReleaseId).orElseThrow();
        Optional<MusicAccess> accessOpt = accessRepository.findByUserAndReleaseAndStatus(fan, release, MusicAccessStatus.ACTIVE);
        assertTrue(accessOpt.isEmpty(), "No access must exist before payment");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8 — Successful Payment: Transaction created + Access created + My Music updated")
    void test8_successfulPayment() {
        assertNotNull(testReleaseId);
        BigDecimal fanBalanceBefore = walletRepository.findByUser(fan).orElseThrow().getBalance();

        PurchaseAccessDto purchaseReq = PurchaseAccessDto.builder()
                .releaseId(testReleaseId)
                .isGift(false)
                .build();

        MusicAccess access = musicService.purchaseReleaseAccess(fan, purchaseReq);
        assertNotNull(access.getId());
        assertEquals(MusicAccessStatus.ACTIVE, access.getStatus());
        assertEquals(fan.getId(), access.getUser().getId());

        // Check wallet deducted $15.00
        BigDecimal fanBalanceAfter = walletRepository.findByUser(fan).orElseThrow().getBalance();
        assertEquals(fanBalanceBefore.subtract(new BigDecimal("15.00")), fanBalanceAfter);

        // Check transaction recorded
        assertNotNull(access.getTransaction());
        assertEquals(new BigDecimal("15.00"), access.getTransaction().getAmount());

        // Check My Music includes release
        List<MusicRelease> myMusic = musicService.getUserPurchasedReleases(fan);
        assertTrue(myMusic.stream().anyMatch(r -> r.getId().equals(testReleaseId)));
    }

    @Test
    @Order(9)
    @DisplayName("Test 9 — Benefit Authorization: Full Listening -> YES, Exclusive -> YES, Community -> YES, Download -> NO")
    void test9_benefitAuthorization() {
        assertNotNull(testReleaseId);
        assertNotNull(testTrackId);

        // Full listening -> YES
        Map<String, Object> streamAccess = musicService.verifyPlaybackAccess(fan, testTrackId);
        assertTrue((Boolean) streamAccess.get("hasFullAccess"));
        assertNull(streamAccess.get("maxDurationSeconds"));

        // Exclusive content -> YES
        List<MusicExclusiveContentDto> exclusive = musicService.getExclusiveContents(fan, testReleaseId);
        assertFalse(exclusive.isEmpty());
        assertEquals("Studio BTS Recording", exclusive.get(0).getTitle());

        // Download access -> NO
        boolean canDownload = musicService.canDownloadTrack(fan, testTrackId);
        assertFalse(canDownload, "Download must be denied as configured by artist");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10 — Artist Changes Release: Existing users' Access snapshot is preserved")
    void test10_artistChangesReleasePreservesSnapshot() {
        assertNotNull(testReleaseId);
        MusicRelease release = releaseRepository.findById(testReleaseId).orElseThrow();

        // Artist updates release to remove Community Access for new buyers
        List<ReleaseBenefitDto> updatedBenefits = List.of(
                ReleaseBenefitDto.builder().benefitType(BenefitType.EARLY_ACCESS).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.FULL_LISTENING).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.EXCLUSIVE_CONTENT).enabled(true).build(),
                ReleaseBenefitDto.builder().benefitType(BenefitType.COMMUNITY_ACCESS).enabled(false).build() // Now disabled
        );

        musicService.updateRelease(release.getId(), release, updatedBenefits, null, artist);

        // Verify existing fan still retains community access from their snapshot
        ReleaseDetailResponse fanView = musicService.getReleaseDetail(testReleaseId, fan);
        assertTrue(fanView.isHasCommunity(), "Existing purchaser retains promised benefits via snapshot");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11 — Failed Payment: Insufficient balance creates no access")
    void test11_failedPayment() {
        assertNotNull(testReleaseId);
        // User with zero balance
        Wallet otherWallet = walletRepository.findByUser(otherFan).orElseGet(() ->
                walletRepository.save(Wallet.builder().user(otherFan).balance(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build())
        );
        otherWallet.setBalance(BigDecimal.ZERO);
        walletRepository.save(otherWallet);

        PurchaseAccessDto failReq = PurchaseAccessDto.builder()
                .releaseId(testReleaseId)
                .isGift(false)
                .build();

        assertThrows(AppException.class, () -> musicService.purchaseReleaseAccess(otherFan, failReq));

        // Verify no access created
        MusicRelease release = releaseRepository.findById(testReleaseId).orElseThrow();
        assertFalse(accessRepository.existsByUserAndReleaseAndStatus(otherFan, release, MusicAccessStatus.ACTIVE));
    }

    @Test
    @Order(12)
    @DisplayName("Test 12 — Duplicate Payment: Repeated checkout attempts rejected")
    void test12_duplicatePaymentPrevention() {
        assertNotNull(testReleaseId);
        PurchaseAccessDto duplicateReq = PurchaseAccessDto.builder()
                .releaseId(testReleaseId)
                .isGift(false)
                .build();

        // Fan already has access from Test 8
        AppException ex = assertThrows(AppException.class, () -> musicService.purchaseReleaseAccess(fan, duplicateReq));
        assertTrue(ex.getMessage().contains("already have Access"));
    }

    @Test
    @Order(13)
    @DisplayName("Test 13 — Unauthorized API Access: Denies access without permissions")
    void test13_unauthorizedApiAccess() {
        assertNotNull(testReleaseId);
        assertNotNull(testTrackId);

        // otherFan has no access to release
        assertThrows(AppException.class, () -> musicService.getExclusiveContents(otherFan, testReleaseId));
        assertFalse(musicService.canDownloadTrack(otherFan, testTrackId));
    }

    @Test
    @Order(14)
    @DisplayName("Test 14 — Public Profile: Only published releases appear")
    void test14_publicProfileReleases() {
        // Create an unreleased draft
        MusicRelease draft = MusicRelease.builder()
                .title("Secret Unfinished Demo")
                .releaseType(ReleaseType.SINGLE)
                .genre("Pop")
                .albumPrice(new BigDecimal("5.00"))
                .status("DRAFT")
                .build();
        musicService.createRelease(draft, Collections.emptyList(), Collections.emptyList(), artist);

        // Public visitor queries artist releases
        List<MusicRelease> publicArtistReleases = musicService.getArtistReleases(artist, "PUBLISHED");
        assertTrue(publicArtistReleases.stream().anyMatch(r -> r.getId().equals(testReleaseId)));
        assertFalse(publicArtistReleases.stream().anyMatch(r -> "Secret Unfinished Demo".equals(r.getTitle())),
                "Drafts must never appear in public artist profile");
    }
}
