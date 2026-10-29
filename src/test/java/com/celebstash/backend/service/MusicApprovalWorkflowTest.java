package com.celebstash.backend.service;

import com.celebstash.backend.dto.music.PurchaseAccessDto;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.Wallet;
import com.celebstash.backend.model.enums.MusicAccessStatus;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.model.enums.ReleaseType;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MusicApprovalWorkflowTest {

    @Mock MusicReleaseRepository releaseRepository;
    @Mock MusicTrackRepository trackRepository;
    @Mock MusicEntitlementRepository entitlementRepository;
    @Mock ReleaseBenefitRepository benefitRepository;
    @Mock MusicExclusiveContentRepository exclusiveContentRepository;
    @Mock MusicWaitlistRepository waitlistRepository;
    @Mock MusicAccessRepository accessRepository;
    @Mock MusicPlayRepository musicPlayRepository;
    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock NotificationService notificationService;
    @Mock ProductRepository productRepository;
    @Mock ConcertRepository concertRepository;
    @Mock ChatConversationRepository chatConversationRepository;
    @Mock ChatParticipantRepository chatParticipantRepository;
    @Mock UserRepository userRepository;
    @Mock FileStorageService fileStorageService;

    @InjectMocks MusicService musicService;

    private User artist;
    private User otherArtist;
    private User fan;
    private User admin;

    @BeforeEach
    void setUp() {
        artist = user(1L, "artist", Role.ARTIST);
        otherArtist = user(2L, "other", Role.ARTIST);
        fan = user(3L, "fan", Role.USER);
        admin = user(4L, "admin", Role.ADMIN);
        when(releaseRepository.save(any(MusicRelease.class))).thenAnswer(inv -> inv.getArgument(0));
        when(waitlistRepository.findByRelease(any())).thenReturn(List.of());
    }

    private static User user(Long id, String username, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        return u;
    }

    private MusicRelease release(Long id, String status) {
        MusicRelease r = MusicRelease.builder()
                .id(id)
                .title("Release " + id)
                .releaseType(ReleaseType.SINGLE)
                .artist(artist)
                .status(status)
                .albumPrice(new BigDecimal("4.99"))
                .tracks(new ArrayList<>())
                .benefits(new ArrayList<>())
                .exclusiveContents(new ArrayList<>())
                .build();
        MusicTrack t = MusicTrack.builder().id(id * 10).title("Track").trackNumber(1).audioFileUrl("a.mp3").release(r).build();
        r.getTracks().add(t);
        when(releaseRepository.findById(id)).thenReturn(Optional.of(r));
        when(trackRepository.findById(t.getId())).thenReturn(Optional.of(t));
        return r;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        AppException ex = assertThrows(AppException.class, action::run);
        assertEquals(expected, ex.getStatus());
    }

    // --- Upload status --------------------------------------------------------------------------

    @Test
    void artistCannotSelfPublishOnUpload() {
        assertEquals(MusicRelease.STATUS_PENDING_REVIEW, musicService.resolveInitialStatus("PUBLISHED", artist));
        assertEquals(MusicRelease.STATUS_PENDING_REVIEW, musicService.resolveInitialStatus(null, artist));
        assertEquals(MusicRelease.STATUS_DRAFT, musicService.resolveInitialStatus("draft", artist));
        assertEquals(MusicRelease.STATUS_PUBLISHED, musicService.resolveInitialStatus("PUBLISHED", admin));
    }

    @Test
    void artistSubmitGoesToReviewNotToPublic() {
        MusicRelease draft = release(10L, MusicRelease.STATUS_DRAFT);
        MusicRelease result = musicService.publishRelease(10L, artist);
        assertEquals(MusicRelease.STATUS_PENDING_REVIEW, result.getStatus());
        assertNotNull(draft.getSubmittedAt());
    }

    @Test
    void artistCannotSubmitAnotherArtistsRelease() {
        release(11L, MusicRelease.STATUS_DRAFT);
        assertStatus(HttpStatus.FORBIDDEN, () -> musicService.publishRelease(11L, otherArtist));
    }

    // --- Visibility -----------------------------------------------------------------------------

    @Test
    void pendingAndRejectedReleasesAreHiddenFromThePublic() {
        MusicRelease pending = release(20L, MusicRelease.STATUS_PENDING_REVIEW);
        MusicRelease rejected = release(21L, MusicRelease.STATUS_REJECTED);

        assertFalse(musicService.canView(pending, null));
        assertFalse(musicService.canView(pending, fan));
        assertFalse(musicService.canView(rejected, otherArtist));
        assertTrue(musicService.canView(pending, artist), "owner sees own submission");
        assertTrue(musicService.canView(pending, admin), "admin sees submissions");

        assertStatus(HttpStatus.NOT_FOUND, () -> musicService.getReleaseDetail(20L, fan));
        assertStatus(HttpStatus.NOT_FOUND, () -> musicService.getVisibleTrack(200L, fan));
    }

    @Test
    void archivedReleaseStaysVisibleOnlyToAccessHolders() {
        MusicRelease archived = release(22L, MusicRelease.STATUS_ARCHIVED);
        when(accessRepository.existsByUserAndReleaseAndStatus(fan, archived, MusicAccessStatus.ACTIVE)).thenReturn(true);
        assertTrue(musicService.canView(archived, fan));
        assertFalse(musicService.canView(archived, otherArtist));
    }

    @Test
    void publicListingOnlyQueriesPublishedReleases() {
        musicService.getAllReleases();
        verify(releaseRepository).findByStatusOrderByCreatedAtDesc(MusicRelease.STATUS_PUBLISHED);
    }

    // --- Admin review ---------------------------------------------------------------------------

    @Test
    void onlyAdminsCanApproveOrReject() {
        release(30L, MusicRelease.STATUS_PENDING_REVIEW);
        assertStatus(HttpStatus.FORBIDDEN, () -> musicService.approveRelease(30L, artist));
        assertStatus(HttpStatus.FORBIDDEN, () -> musicService.rejectRelease(30L, fan, "no"));
    }

    @Test
    void approvalPublishesAndNotifiesTheArtist() {
        MusicRelease pending = release(31L, MusicRelease.STATUS_PENDING_REVIEW);
        pending.setRejectionReason("old reason");

        MusicRelease approved = musicService.approveRelease(31L, admin);

        assertEquals(MusicRelease.STATUS_PUBLISHED, approved.getStatus());
        assertNotNull(approved.getApprovedAt());
        assertNull(approved.getRejectionReason());
        verify(notificationService).createNotification(eq(artist), anyString(), anyString(), eq(NotificationType.MUSIC_RELEASE_APPROVED), eq(31L));
    }

    @Test
    void rejectionRequiresAReasonAndKeepsTheSubmission() {
        release(32L, MusicRelease.STATUS_PENDING_REVIEW);
        assertStatus(HttpStatus.BAD_REQUEST, () -> musicService.rejectRelease(32L, admin, "  "));

        MusicRelease rejected = musicService.rejectRelease(32L, admin, "Cover art is low resolution");
        assertEquals(MusicRelease.STATUS_REJECTED, rejected.getStatus());
        assertEquals("Cover art is low resolution", rejected.getRejectionReason());
        verify(releaseRepository, never()).delete(any());
        verify(notificationService).createNotification(eq(artist), anyString(), contains("Cover art"), eq(NotificationType.MUSIC_RELEASE_REJECTED), eq(32L));
    }

    @Test
    void cannotApproveADraftThatWasNeverSubmitted() {
        release(33L, MusicRelease.STATUS_DRAFT);
        assertStatus(HttpStatus.CONFLICT, () -> musicService.approveRelease(33L, admin));
    }

    // --- Purchase & playback --------------------------------------------------------------------

    @Test
    void unpublishedMusicCannotBePurchased() {
        release(40L, MusicRelease.STATUS_PENDING_REVIEW);
        PurchaseAccessDto req = new PurchaseAccessDto();
        req.setReleaseId(40L);
        assertStatus(HttpStatus.NOT_FOUND, () -> musicService.purchaseReleaseAccess(fan, req));
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void duplicatePurchaseIsRejectedBeforeCharging() {
        MusicRelease live = release(41L, MusicRelease.STATUS_PUBLISHED);
        Wallet wallet = Wallet.builder().id(9L).user(fan).balance(new BigDecimal("100")).createdAt(LocalDateTime.now()).build();
        when(walletRepository.findByUser(fan)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(wallet));
        when(accessRepository.existsByUserAndReleaseAndStatus(fan, live, MusicAccessStatus.ACTIVE)).thenReturn(true);

        PurchaseAccessDto req = new PurchaseAccessDto();
        req.setReleaseId(41L);
        assertStatus(HttpStatus.CONFLICT, () -> musicService.purchaseReleaseAccess(fan, req));
        assertEquals(new BigDecimal("100"), wallet.getBalance(), "wallet must not be charged");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void insufficientBalanceGrantsNothing() {
        release(42L, MusicRelease.STATUS_PUBLISHED);
        Wallet wallet = Wallet.builder().id(9L).user(fan).balance(new BigDecimal("1.00")).createdAt(LocalDateTime.now()).build();
        when(walletRepository.findByUser(fan)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(wallet));

        PurchaseAccessDto req = new PurchaseAccessDto();
        req.setReleaseId(42L);
        req.setPrice(new BigDecimal("0.01")); // client-sent price is ignored
        assertStatus(HttpStatus.BAD_REQUEST, () -> musicService.purchaseReleaseAccess(fan, req));
        verify(accessRepository, never()).save(any());
    }

    @Test
    void fullPlaybackRequiresEntitlement() {
        MusicRelease live = release(50L, MusicRelease.STATUS_PUBLISHED);
        when(accessRepository.findByUserAndReleaseAndStatus(fan, live, MusicAccessStatus.ACTIVE)).thenReturn(Optional.empty());

        Map<String, Object> anonymous = musicService.verifyPlaybackAccess(null, 500L);
        Map<String, Object> unpaid = musicService.verifyPlaybackAccess(fan, 500L);
        Map<String, Object> owner = musicService.verifyPlaybackAccess(artist, 500L);
        Map<String, Object> reviewer = musicService.verifyPlaybackAccess(admin, 500L);

        assertEquals(false, anonymous.get("hasFullAccess"));
        assertEquals(false, unpaid.get("hasFullAccess"));
        assertEquals(5, unpaid.get("maxDurationSeconds"));
        assertEquals(true, owner.get("hasFullAccess"));
        assertEquals(true, reviewer.get("hasFullAccess"));
    }

    @Test
    void unarchiveWithoutPriorApprovalReturnsToDraft() {
        MusicRelease archived = release(60L, MusicRelease.STATUS_ARCHIVED);
        assertEquals(MusicRelease.STATUS_DRAFT, musicService.unarchiveRelease(60L, artist).getStatus());

        archived.setStatus(MusicRelease.STATUS_ARCHIVED);
        archived.setApprovedAt(LocalDateTime.now());
        assertEquals(MusicRelease.STATUS_PUBLISHED, musicService.unarchiveRelease(60L, artist).getStatus());
    }
}
