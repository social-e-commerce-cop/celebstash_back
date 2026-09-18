package com.celebstash.backend.repository;

import com.celebstash.backend.model.Order;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Find order by order number
    Optional<Order> findByOrderNumber(String orderNumber);
    
    // Find all orders for a specific user
    List<Order> findByUser(User user);
    
    // Find all orders for a specific user with pagination
    Page<Order> findByUser(User user, Pageable pageable);
    
    // Find all orders for a specific user with a specific status
    List<Order> findByUserAndStatus(User user, OrderStatus status);
    
    // Find all orders for a specific user with a specific status with pagination
    Page<Order> findByUserAndStatus(User user, OrderStatus status, Pageable pageable);
    
    // Find all orders with a specific status
    List<Order> findByStatus(OrderStatus status);
    
    // Find all orders with a specific status with pagination
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    
    // Find all orders created between two dates
    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Find all orders created between two dates with pagination
    Page<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Find all orders for a specific user created between two dates
    List<Order> findByUserAndCreatedAtBetween(User user, LocalDateTime start, LocalDateTime end);
    
    // Find all orders for a specific user created between two dates with pagination
    Page<Order> findByUserAndCreatedAtBetween(User user, LocalDateTime start, LocalDateTime end, Pageable pageable);
}