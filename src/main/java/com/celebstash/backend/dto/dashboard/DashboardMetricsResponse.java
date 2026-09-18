package com.celebstash.backend.dto.dashboard;

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
public class DashboardMetricsResponse {

    private long totalUsers;
    private long totalArtists;
    private long totalProducts;
    private double totalEarnings;
    
    // For Donut Chart
    private List<EntityCount> entityCounts;
    
    // For Line Chart (User Growth)
    private List<MonthlyGrowth> userGrowth;
    
    // For Bar Chart (Revenue)
    private List<MonthlyRevenue> revenueData;

    @Data
    @AllArgsConstructor
    public static class EntityCount {
        private String name;
        private long value;
        private String color;
    }

    @Data
    @AllArgsConstructor
    public static class MonthlyGrowth {
        private String month;
        private long artists;
        private long users;
    }

    @Data
    @AllArgsConstructor
    public static class MonthlyRevenue {
        private String month;
        private double revenue;
    }
}
