package com.celebstash.backend.service;

import com.celebstash.backend.dto.dashboard.DashboardMetricsResponse;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.model.enums.TransactionStatus;
import com.celebstash.backend.model.enums.TransactionType;
import com.celebstash.backend.repository.ProductRepository;
import com.celebstash.backend.repository.TransactionRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final TransactionRepository transactionRepository;

    public DashboardMetricsResponse getMetrics() {
        List<User> allUsers = userRepository.findAll();
        long totalUsersCount = allUsers.size();
        long totalUsers = allUsers.stream().filter(u -> u.getRole() == Role.USER).count();
        long totalArtists = allUsers.stream().filter(u -> u.getRole() == Role.ARTIST).count();
        long totalProducts = productRepository.count();

        // Calculate total earnings (sum of all completed purchases or transfers)
        double totalEarnings = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED && 
                             (t.getType() == TransactionType.PURCHASE || t.getType() == TransactionType.DEPOSIT))
                .mapToDouble(t -> t.getAmount().doubleValue())
                .sum();

        long usersDisplay = totalUsersCount > 0 ? totalUsersCount : totalUsers;

        // Entity Counts for Donut Chart
        List<DashboardMetricsResponse.EntityCount> entityCounts = new ArrayList<>();
        entityCounts.add(new DashboardMetricsResponse.EntityCount("Users", usersDisplay, "#7126D0"));
        entityCounts.add(new DashboardMetricsResponse.EntityCount("Artists", totalArtists, "#A78BFA"));
        entityCounts.add(new DashboardMetricsResponse.EntityCount("Products", totalProducts, "#10B981"));

        // Snapshot of current counts only — no fabricated historical series
        List<DashboardMetricsResponse.MonthlyGrowth> userGrowth = new ArrayList<>();
        userGrowth.add(new DashboardMetricsResponse.MonthlyGrowth("Current", totalArtists, usersDisplay));

        List<DashboardMetricsResponse.MonthlyRevenue> revenueData = new ArrayList<>();
        revenueData.add(new DashboardMetricsResponse.MonthlyRevenue("Current", totalEarnings));

        return DashboardMetricsResponse.builder()
                .totalUsers(usersDisplay)
                .totalArtists(totalArtists)
                .totalProducts(totalProducts)
                .totalEarnings(totalEarnings)
                .entityCounts(entityCounts)
                .userGrowth(userGrowth)
                .revenueData(revenueData)
                .build();
    }
}
