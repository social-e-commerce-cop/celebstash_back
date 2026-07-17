package com.celebstash.backend.dto.user;

import com.celebstash.backend.model.enums.Gender;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String username;

    private Gender gender;

    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;

    private String profilePicture;

    private String fandomName;
}