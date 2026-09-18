package com.celebstash.backend.repository;

import com.celebstash.backend.model.Location;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    
    // Find all active locations
    List<Location> findByActiveTrue();
    
    // Find all locations for a specific user
    List<Location> findByUser(User user);
    
    // Find all active locations for a specific user
    List<Location> findByUserAndActiveTrue(User user);
    
    // Check if a location exists for a user
    boolean existsByUserAndId(User user, Long id);
}