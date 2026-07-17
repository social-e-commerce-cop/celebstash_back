package com.celebstash.backend.repository;

import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ProductStatus;
import com.celebstash.backend.model.enums.ProductType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // Find all products with a specific status
    List<Product> findByStatus(ProductStatus status);

    // Find all products by seller
    List<Product> findBySeller(User seller);

    // Find all products by seller and status
    List<Product> findBySellerAndStatus(User seller, ProductStatus status);

    // Find all products by product type
    List<Product> findByProductType(ProductType productType);

    // Find all products by product type and status
    List<Product> findByProductTypeAndStatus(ProductType productType, ProductStatus status);

    // Find all bidding products with an end time before the given time
    List<Product> findByProductTypeAndBidEndTimeBefore(ProductType productType, LocalDateTime endTime);
}
