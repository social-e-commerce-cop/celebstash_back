package com.celebstash.backend.repository;

import com.celebstash.backend.model.Order;
import com.celebstash.backend.model.OrderItem;
import com.celebstash.backend.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    // Find all order items for a specific order
    List<OrderItem> findByOrder(Order order);
    
    // Find all order items for a specific product
    List<OrderItem> findByProduct(Product product);
    
    // Count order items for a specific order
    long countByOrder(Order order);
    
    // Count order items for a specific product
    long countByProduct(Product product);
    
    // Delete all order items for a specific order
    void deleteByOrder(Order order);
}