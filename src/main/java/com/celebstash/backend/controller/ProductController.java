package com.celebstash.backend.controller;

import com.celebstash.backend.dto.product.ProductCreateRequest;
import com.celebstash.backend.dto.product.ProductRequest;
import com.celebstash.backend.dto.product.ProductResponse;
import com.celebstash.backend.dto.product.ProductStatusUpdateRequest;
import com.celebstash.backend.model.enums.ProductType;
import com.celebstash.backend.service.ProductService;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Product", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product", description = "Creates a new product with PENDING status")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return new ResponseEntity<>(productService.createProduct(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update and resubmit a product", description = "Allows seller to edit product details and resubmit for admin approval (status resets to PENDING)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Create a new product with file uploads", description = "Creates a new product with file uploads and PENDING status")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponse> createProductWithFiles(
            @RequestParam("name") @NotBlank @Size(min = 3, max = 100) String name,
            @RequestParam(value = "description", required = false) @Size(max = 1000) String description,
            @RequestParam("price") @NotNull @Min(0) BigDecimal price,
            @RequestParam("images") @Size(min = 3, max = 5) List<MultipartFile> images,
            @RequestParam(value = "video", required = false) MultipartFile video,
            @RequestParam("stockQuantity") @NotNull @Min(0) Integer stockQuantity,
            @RequestParam(value = "productType", required = false, defaultValue = "REGULAR") ProductType productType) {

        ProductCreateRequest request = ProductCreateRequest.builder()
                .name(name)
                .description(description)
                .price(price)
                .images(images)
                .video(video)
                .stockQuantity(stockQuantity)
                .productType(productType)
                .build();

        return new ResponseEntity<>(productService.createProductWithFiles(request), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all products", description = "Returns all approved products for regular users, all products for admins")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/new-drops")
    @Operation(summary = "Get new drop products", description = "Returns newly approved products for public shop display")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ProductResponse>> getNewDrops() {
        return ResponseEntity.ok(productService.getNewDrops());
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending products", description = "Returns all pending products waiting for admin approval")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProductResponse>> getPendingProducts() {
        return ResponseEntity.ok(productService.getPendingProducts());
    }

    @GetMapping("/my-products")
    @Operation(summary = "Get my products", description = "Returns all products created by the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ProductResponse>> getMyProducts() {
        return ResponseEntity.ok(productService.getMyProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns a product by its ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update product status", description = "Updates the status of a product (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProductStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusUpdateRequest request) {
        return ResponseEntity.ok(productService.updateProductStatus(id, request));
    }

    @PatchMapping("/{id}/move-to-bidding")
    @Operation(summary = "Move product to bidding", description = "Moves a product to the bidding section (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> moveProductToBidding(
            @PathVariable Long id,
            @RequestParam @NotNull @Min(value = 0, message = "Initial bid price must be greater than or equal to 0") BigDecimal initialBidPrice) {
        return ResponseEntity.ok(productService.moveProductToBidding(id, initialBidPrice));
    }
}
