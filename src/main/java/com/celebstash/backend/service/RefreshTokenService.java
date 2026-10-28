package com.celebstash.backend.service;

import com.celebstash.backend.model.RefreshToken;
import com.celebstash.backend.model.User;
import com.celebstash.backend.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    /**
     * Prefix marking a token stored as a SHA-256 digest.
     *
     * <p>Refresh tokens are random 122-bit UUIDs, so a fast deterministic digest is safe and, unlike
     * bcrypt, can be looked up by index. Tokens issued before this change were stored as bcrypt
     * hashes; those are still accepted through {@link #findLegacyBcryptToken} until they expire.
     */
    private static final String SHA256_PREFIX = "sha256:";

    private static String digest(String tokenValue) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return SHA256_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Transactional
    public RefreshToken createRefreshToken(User user, HttpServletRequest request) {
        String tokenValue = UUID.randomUUID().toString();
        String hashedToken = digest(tokenValue);
        
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(hashedToken)
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
                .issuedAt(Instant.now())
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .revoked(false)
                .build();
        
        refreshTokenRepository.save(refreshToken);
        
        // Return the token with the plain value for the response
        refreshToken.setToken(tokenValue);
        return refreshToken;
    }

    @Transactional
    public Optional<RefreshToken> validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> stored = refreshTokenRepository.findByToken(digest(token));
        if (stored.isEmpty()) {
            stored = findLegacyBcryptToken(token);
        }
        return stored.filter(t -> !t.isRevoked() && !t.isExpired());
    }

    /**
     * Previously every refresh compared the presented token against every row in the table with
     * bcrypt, so each refresh got slower as tokens accumulated. Only live legacy rows are scanned
     * now, and that set shrinks to nothing as old tokens expire or are rotated.
     */
    private Optional<RefreshToken> findLegacyBcryptToken(String token) {
        return refreshTokenRepository.findActiveLegacyTokens(Instant.now(), SHA256_PREFIX + "%").stream()
                .filter(storedToken -> passwordEncoder.matches(token, storedToken.getToken()))
                .findFirst();
    }

    @Transactional
    public void revokeRefreshToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        log.info("Refresh token revoked: {}", token.getId());
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("All refresh tokens revoked for user: {}", user.getUsername());
    }

    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken, HttpServletRequest request) {
        // Revoke the old token
        revokeRefreshToken(oldToken);
        
        // Create a new token
        return createRefreshToken(oldToken.getUser(), request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}