package com.celebstash.backend.repository;

import com.celebstash.backend.model.PremiumContent;
import com.celebstash.backend.model.UnlockedContent;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnlockedContentRepository extends JpaRepository<UnlockedContent, Long> {
    Optional<UnlockedContent> findByUserAndContent(User user, PremiumContent content);
    List<UnlockedContent> findByUser(User user);
}
