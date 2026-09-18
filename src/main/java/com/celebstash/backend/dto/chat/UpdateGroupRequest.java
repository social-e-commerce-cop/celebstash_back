package com.celebstash.backend.dto.chat;

public record UpdateGroupRequest(
    String name,
    String description,
    String avatarUrl
) {}
