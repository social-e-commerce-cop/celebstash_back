package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.TicketTier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketTier ticketTier;

    @Column(nullable = false, unique = true)
    private String verificationCode;

    @Column(nullable = false)
    private boolean isUsed;

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @PrePersist
    protected void onCreate() {
        purchasedAt = LocalDateTime.now();
        isUsed = false;
    }
}
