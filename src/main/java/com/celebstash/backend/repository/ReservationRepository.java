package com.celebstash.backend.repository;

import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.Reservation;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime dateTime);
    Optional<Reservation> findByUserAndProductAndStatus(User user, Product product, ReservationStatus status);
}
