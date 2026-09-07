package com.celebstash.backend.dto.chat;

public record UserSearchDto(
    Long id,
    String fullName,
    String username,
    String profilePicture,
    String bio
) {}
