package com.celebstash.backend.repository;

import com.celebstash.backend.model.RefreshToken;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findAllByUser(User user);
    
    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    void revokeAllUserTokens(User user);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    void deleteAllByUser(User user);
    
    boolean existsByTokenAndRevokedFalse(String token);

    /** Unrevoked, unexpired tokens still stored in the legacy bcrypt format. */
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user WHERE r.revoked = false AND r.expiryDate > :now AND r.token NOT LIKE :digestPattern")
    List<RefreshToken> findActiveLegacyTokens(@Param("now") java.time.Instant now, @Param("digestPattern") String digestPattern);
}