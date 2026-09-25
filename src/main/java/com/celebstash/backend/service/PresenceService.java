package com.celebstash.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is online and when they were last seen.
 *
 * <p>Presence is deliberately kept out of PostgreSQL: it changes on every heartbeat, and writing
 * that volume of updates to the users table would be far more expensive than the feature is worth.
 * State lives in Redis under a short TTL, so a client that disappears without a clean disconnect
 * simply stops refreshing its key and ages out instead of appearing online forever.
 *
 * <p>Redis is optional. The OTP service already treats it as best-effort, and this follows the
 * same rule: if Redis is unreachable the service falls back to a per-instance in-memory map so
 * local development and single-instance deployments keep working. The fallback is not shared
 * between instances, which is noted as a limitation rather than hidden.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    /** A client must heartbeat more often than this or it is treated as offline. */
    private static final Duration ONLINE_TTL = Duration.ofSeconds(60);
    /** Last-seen outlives the online key so "last seen 3 days ago" still works. */
    private static final Duration LAST_SEEN_TTL = Duration.ofDays(30);

    private static final String ONLINE_KEY = "presence:online:";
    private static final String LAST_SEEN_KEY = "presence:lastseen:";
    private static final String ACTIVE_CONV_KEY = "presence:activeconv:";

    private final RedisTemplate<String, Object> redisTemplate;

    // Fallback used only when Redis is unavailable.
    private final Map<Long, Instant> localOnline = new ConcurrentHashMap<>();
    private final Map<Long, Instant> localLastSeen = new ConcurrentHashMap<>();
    private final Map<Long, Long> localActiveConversation = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable = true;

    /** Refreshes the online window for a user. Called on connect and on periodic heartbeats. */
    public void heartbeat(Long userId) {
        if (userId == null) return;
        Instant now = Instant.now();
        localOnline.put(userId, now);
        localLastSeen.put(userId, now);
        withRedis(() -> {
            redisTemplate.opsForValue().set(ONLINE_KEY + userId, now.toString(), ONLINE_TTL);
            redisTemplate.opsForValue().set(LAST_SEEN_KEY + userId, now.toString(), LAST_SEEN_TTL);
        });
    }

    /** Marks a user offline and stamps last-seen. Called on disconnect. */
    public void markOffline(Long userId) {
        if (userId == null) return;
        Instant now = Instant.now();
        localOnline.remove(userId);
        localLastSeen.put(userId, now);
        withRedis(() -> {
            redisTemplate.delete(ONLINE_KEY + userId);
            redisTemplate.opsForValue().set(LAST_SEEN_KEY + userId, now.toString(), LAST_SEEN_TTL);
        });
    }

    /**
     * Records which conversation a user currently has open, so a notification is not sent for a
     * thread they are already reading (§34). Driven by STOMP subscribe/unsubscribe, which also
     * means it clears itself if the client vanishes.
     */
    public void setActiveConversation(Long userId, Long conversationId) {
        if (userId == null || conversationId == null) return;
        localActiveConversation.put(userId, conversationId);
        withRedis(() -> redisTemplate.opsForValue()
                .set(ACTIVE_CONV_KEY + userId, String.valueOf(conversationId), ONLINE_TTL));
    }

    public void clearActiveConversation(Long userId) {
        if (userId == null) return;
        localActiveConversation.remove(userId);
        withRedis(() -> redisTemplate.delete(ACTIVE_CONV_KEY + userId));
    }

    /** True when the user is currently looking at this conversation. */
    public boolean isViewing(Long userId, Long conversationId) {
        if (userId == null || conversationId == null) return false;
        if (redisAvailable) {
            try {
                Object v = redisTemplate.opsForValue().get(ACTIVE_CONV_KEY + userId);
                if (v != null) {
                    return String.valueOf(conversationId).equals(String.valueOf(v));
                }
            } catch (Exception e) {
                degrade(e);
            }
        }
        return conversationId.equals(localActiveConversation.get(userId));
    }

    public boolean isOnline(Long userId) {
        if (userId == null) return false;
        if (redisAvailable) {
            try {
                Boolean exists = redisTemplate.hasKey(ONLINE_KEY + userId);
                if (exists != null) {
                    return exists;
                }
            } catch (Exception e) {
                degrade(e);
            }
        }
        Instant seen = localOnline.get(userId);
        return seen != null && seen.isAfter(Instant.now().minus(ONLINE_TTL));
    }

    /** ISO-8601 instant, or null when never seen. */
    public String getLastSeen(Long userId) {
        if (userId == null) return null;
        if (redisAvailable) {
            try {
                Object v = redisTemplate.opsForValue().get(LAST_SEEN_KEY + userId);
                if (v != null) {
                    return String.valueOf(v);
                }
            } catch (Exception e) {
                degrade(e);
            }
        }
        Instant seen = localLastSeen.get(userId);
        return seen != null ? seen.toString() : null;
    }

    private void withRedis(Runnable op) {
        if (!redisAvailable) return;
        try {
            op.run();
        } catch (Exception e) {
            degrade(e);
        }
    }

    private void degrade(Exception e) {
        if (redisAvailable) {
            redisAvailable = false;
            log.warn("Redis unavailable for presence; falling back to in-memory tracking "
                    + "(not shared across instances). Cause: {}", e.getMessage());
        }
    }
}
