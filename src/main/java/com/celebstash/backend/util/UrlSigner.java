package com.celebstash.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class UrlSigner {

    @Value("${app.jwt.secret}")
    private String secretKey;

    public String generateSignature(String path, long expiresAt) {
        try {
            String message = path + ":" + expiresAt;
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKeySpec);
            byte[] hash = sha256HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error signing URL", e);
        }
    }

    public boolean verifySignature(String path, long expiresAt, String signature) {
        if (System.currentTimeMillis() > expiresAt) {
            return false;
        }
        String expectedSignature = generateSignature(path, expiresAt);
        return expectedSignature.equals(signature);
    }
}
