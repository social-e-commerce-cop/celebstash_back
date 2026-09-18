package com.celebstash.backend.dto.music;

import com.celebstash.backend.model.enums.ExclusiveContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicExclusiveContentDto {
    private Long id;
    private String title;
    private String description;
    private ExclusiveContentType contentType;
    private String mediaUrl;
    private String thumbnailUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
