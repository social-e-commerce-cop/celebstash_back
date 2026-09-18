package com.celebstash.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Address settings
    private String addressDescription;
    private String streetAddress;
    private String city;
    private String state;
    private String zipCode;

    // Notification settings
    private boolean notificationsEnabled;

    // Wallet settings
    private boolean pinVerificationEnabled;
    private boolean accountLocked;

    // Security settings
    private String passcode; // Hashed passcode

    // Language settings
    private String language;

    // Privacy settings
    private boolean privacyPolicyAccepted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Default settings
        if (language == null) {
            language = "en";
        }
        if (notificationsEnabled == false) {
            notificationsEnabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}