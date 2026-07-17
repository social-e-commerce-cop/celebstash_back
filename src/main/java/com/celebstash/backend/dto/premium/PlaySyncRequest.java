package com.celebstash.backend.dto.premium;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaySyncRequest {

    @NotNull(message = "Content ID is required")
    private Long contentId;

    @NotNull(message = "Plays count is required")
    @Min(value = 1, message = "Plays count must be at least 1")
    private Integer playsCount;
}
