package com.celebstash.backend.repository;

import com.celebstash.backend.model.Concert;
import com.celebstash.backend.model.Ticket;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByUser(User user);
    List<Ticket> findByConcert(Concert concert);
    Optional<Ticket> findByVerificationCode(String verificationCode);
}
