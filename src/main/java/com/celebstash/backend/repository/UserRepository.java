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
    
    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.email = :val OR u.phoneNumber = :val OR u.username = :val")
    Optional<User> findByEmailOrPhoneOrUsername(@Param("val") String val);

    boolean existsByEmail(String email);
    
    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByNameOrUsername(@Param("query") String query);
}