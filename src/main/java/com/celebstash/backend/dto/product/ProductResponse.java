package com.celebstash.backend.dto.product;

import com.celebstash.backend.model.enums.ProductStatus;
import com.celebstash.backend.model.enums.ProductType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private List<String> imageUrls;
    private String videoUrl;
    private Integer stockQuantity;
    private String category;
    private java.util.Map<String, Integer> sizeStock;
    private List<String> availableColors;
    private ProductStatus status;
    private ProductType productType;
    private Long sellerId;
    private String sellerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private String adminNotes;

    // Bidding related fields
    private BigDecimal initialBidPrice;
    private BigDecimal currentBidPrice;
    private Long currentBidderId;
    private String currentBidderName;
    private LocalDateTime bidStartTime;
    private LocalDateTime bidEndTime;
    private boolean isBiddingActive;

    // Post information
    private boolean hasPost;
    private Long postId;
}
