package com.celebstash.backend.repository;

import com.celebstash.backend.model.KycRequest;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycRepository extends JpaRepository<KycRequest, Long> {
    Optional<KycRequest> findByUser(User user);
    List<KycRequest> findByStatus(KycStatus status);
}
