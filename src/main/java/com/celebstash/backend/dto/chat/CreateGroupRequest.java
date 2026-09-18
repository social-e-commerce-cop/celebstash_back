package com.celebstash.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGroupRequest(
    @NotBlank String name,
    String description,
    String avatarUrl,
    @NotEmpty @Size(min = 1) List<Long> memberIds
) {}
