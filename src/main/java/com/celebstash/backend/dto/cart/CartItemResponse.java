package com.celebstash.backend.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productDescription;
    private String productImageUrl;
    private BigDecimal productPrice;
    private Integer quantity;
    private String selectedSize;
    private String selectedColor;
    private Integer stockAvailable; // units still available beyond what this cart line holds
    private boolean available; // false once the product is withdrawn from sale
    private BigDecimal subtotal; // price * quantity
}