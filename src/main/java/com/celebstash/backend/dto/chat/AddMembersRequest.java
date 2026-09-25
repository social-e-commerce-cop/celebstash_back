package com.celebstash.backend.dto.chat;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Ids only — the server resolves each user and re-checks the caller's admin rights. */
public record AddMembersRequest(
        @NotEmpty(message = "At least one user id is required") List<Long> userIds
) {}
