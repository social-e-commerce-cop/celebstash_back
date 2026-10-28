package com.celebstash.backend.dto.story;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One viewer of a story, as shown to the story's author. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryViewerResponse {
    private Long userId;
    private String fullName;
    private String username;
    private String avatar;
    private Instant viewedAt;
    /** The viewer's reaction emoji, or null if they only viewed. */
    private String reaction;
}
