package com.celebstash.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicDTO {

    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String phoneNumber;
    private String bio;
    private String profilePicture;
    private String role;
    private String status;
    private boolean accountVerified;
    private String fandomName;
    private long followersCount;
    private long followingCount;
    private LocalDateTime createdAt;
}
