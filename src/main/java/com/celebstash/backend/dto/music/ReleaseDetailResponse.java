package com.celebstash.backend.dto.music;

import com.celebstash.backend.model.MusicRelease;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseDetailResponse {
    private MusicRelease release;
    private List<ReleaseBenefitDto> benefits;
    private boolean hasAccess;
    private boolean isArtistOwner;
    private boolean canStreamFull;
    private boolean canDownload;
    private boolean hasCommunity;
    private boolean hasExclusiveContent;
    private boolean isWaitlisted;
    private long waitlistCount;
    private List<Object> connectedProducts;
    private Object connectedEvent;
    private Long communityConversationId;
}
