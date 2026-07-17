package com.celebstash.backend.dto.settings;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsUpdateRequest {

    // Address settings
    @Size(max = 500, message = "Address description cannot exceed 500 characters")
    private String addressDescription;

    @Size(max = 255, message = "Street address cannot exceed 255 characters")
    private String streetAddress;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @Size(max = 20, message = "Zip code cannot exceed 20 characters")
    private String zipCode;

    // Notification settings
    private Boolean notificationsEnabled;

    // Wallet settings
    private Boolean pinVerificationEnabled;
    private Boolean accountLocked;

    // Security settings
    private String currentPasscode;
    private String newPasscode;
    private String confirmPasscode;

    // Language settings
    private String language;

    // Privacy settings
    private Boolean privacyPolicyAccepted;
}