package com.celebstash.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUserDTO {

    private Long id;
    private String fullName;
    private String username;
    private String profilePicture;
    private boolean accountVerified;
    private String relationship;
}
