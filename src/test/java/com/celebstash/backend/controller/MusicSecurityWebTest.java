package com.celebstash.backend.controller;

import com.celebstash.backend.config.PasswordEncoderConfig;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ReleaseType;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.MusicTrackRepository;
import com.celebstash.backend.repository.UserRepository;
import com.celebstash.backend.security.config.SecurityConfig;
import com.celebstash.backend.security.jwt.JwtUtils;
import com.celebstash.backend.service.FileStorageService;
import com.celebstash.backend.service.MusicService;
import com.celebstash.backend.util.UrlSigner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifies the HTTP-level rules: who may review music, and that full audio never leaks. */
@WebMvcTest(controllers = {MusicController.class, FileController.class})
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
class MusicSecurityWebTest {

    @Autowired MockMvc mvc;

    @MockitoBean MusicService musicService;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MusicTrackRepository musicTrackRepository;
    @MockitoBean JwtUtils jwtUtils;
    @MockitoBean UrlSigner urlSigner;
    @MockitoBean UserDetailsService userDetailsService;

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRole(role);
        return u;
    }

    private static UsernamePasswordAuthenticationToken as(User u) {
        return new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
    }

    @Test
    void reviewQueueRequiresAnAdministrator() throws Exception {
        mvc.perform(get("/api/music/admin/releases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/music/admin/releases").with(authentication(as(user(1, Role.ARTIST)))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/music/admin/releases").with(authentication(as(user(2, Role.ADMIN)))))
                .andExpect(status().isOk());
    }

    @Test
    void artistsCannotApproveOrRejectMusic() throws Exception {
        User artist = user(1, Role.ARTIST);
        mvc.perform(post("/api/music/admin/releases/5/approve").with(authentication(as(artist))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/music/admin/releases/5/reject").with(authentication(as(artist)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        verify(musicService, never()).approveRelease(anyLong(), any());
        verify(musicService, never()).rejectRelease(anyLong(), any(), any());
    }

    @Test
    void adminCanApprove() throws Exception {
        User admin = user(2, Role.ADMIN);
        MusicRelease approved = MusicRelease.builder().id(5L).title("t").releaseType(ReleaseType.SINGLE)
                .artist(user(1, Role.ARTIST)).status(MusicRelease.STATUS_PUBLISHED).build();
        when(musicService.approveRelease(eq(5L), any())).thenReturn(approved);

        mvc.perform(post("/api/music/admin/releases/5/approve").with(authentication(as(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void unentitledListenerOnlyReceivesThePreviewBytes() throws Exception {
        MusicRelease release = MusicRelease.builder().id(7L).title("t").releaseType(ReleaseType.SINGLE)
                .artist(user(1, Role.ARTIST)).status(MusicRelease.STATUS_PUBLISHED).tracks(new ArrayList<>()).build();
        MusicTrack track = MusicTrack.builder().id(70L).title("Song").audioFileUrl("song.mp3").release(release).previewDurationSeconds(5).build();
        byte[] preview = new byte[]{1, 2, 3};
        when(musicService.getVisibleTrack(eq(70L), any())).thenReturn(track);
        when(musicService.verifyPlaybackAccess(any(), eq(70L))).thenReturn(Map.of("hasFullAccess", false));
        when(musicService.loadPreviewAudio(track)).thenReturn(preview);

        mvc.perform(get("/api/music/tracks/70/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Preview-Only", "true"))
                .andExpect(content().bytes(preview));
        verify(musicService, never()).loadFullAudio(any());
    }

    @Test
    void streamOfUnpublishedTrackIsNotFound() throws Exception {
        when(musicService.getVisibleTrack(eq(71L), any())).thenThrow(new AppException("Track not found", HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/music/tracks/71/stream")).andExpect(status().isNotFound());
    }

    @Test
    void protectedTrackAudioCannotBeFetchedAsAPlainFile() throws Exception {
        when(musicTrackRepository.isProtectedAudioFile("secret-song.mp3")).thenReturn(true);
        mvc.perform(get("/api/files/secret-song.mp3")).andExpect(status().isNotFound());
        verify(fileStorageService, never()).getStoredFile(anyString());
    }

    @Test
    void releaseJsonDoesNotExposeTheAudioStoragePath() throws Exception {
        MusicRelease release = MusicRelease.builder().id(8L).title("t").releaseType(ReleaseType.SINGLE)
                .artist(user(1, Role.ARTIST)).status(MusicRelease.STATUS_PUBLISHED).tracks(new ArrayList<>()).build();
        release.getTracks().add(MusicTrack.builder().id(80L).title("Song").audioFileUrl("secret-song.mp3").release(release).build());
        when(musicService.getAllReleases()).thenReturn(List.of(release));

        mvc.perform(get("/api/music/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tracks[0].title").value("Song"))
                .andExpect(jsonPath("$[0].tracks[0].audioFileUrl").doesNotExist());
    }
}
