package com.celebstash.backend.dto.music;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistStudioStatsDto {
    private long totalReleases;
    private long draftReleases;
    private long publishedReleases;
    private long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal artistPayouts;
    private long totalFans;
    private List<Object> recentSales;
}
