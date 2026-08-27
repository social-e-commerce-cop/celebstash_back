package com.celebstash.backend.repository;

import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByPhoneNumber(String phoneNumber);
    
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);
    
    boolean existsByEmail(String email);
    
    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT u FROM User u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByNameOrUsername(@Param("query") String query);
}