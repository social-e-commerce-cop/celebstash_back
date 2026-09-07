package com.celebstash.backend.dto.chat;

import java.util.List;

public record CreateConversationRequest(
    Long targetUserId
) {}
