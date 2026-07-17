package com.celebstash.backend.repository;

import com.celebstash.backend.model.PremiumContent;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PremiumContentRepository extends JpaRepository<PremiumContent, Long> {
    List<PremiumContent> findByCreator(User creator);
}
