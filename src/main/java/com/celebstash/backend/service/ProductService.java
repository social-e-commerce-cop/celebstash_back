package com.celebstash.backend.service;

import com.celebstash.backend.dto.product.ProductCreateRequest;
import com.celebstash.backend.dto.product.ProductRequest;
import com.celebstash.backend.dto.product.ProductResponse;
import com.celebstash.backend.dto.product.ProductStatusUpdateRequest;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Product;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ProductStatus;
import com.celebstash.backend.model.enums.ProductType;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.PostRepository;
import com.celebstash.backend.repository.ProductRepository;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserService userService;
    private final PostRepository postRepository;
    private final FileStorageService fileStorageService;

    /**
     * Only approved artists (and admins) may list products. Selling rights come from an
     * approved artist application — self-promotion here would bypass admin review entirely.
     */
    private User requireSellerRole() {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() != Role.ARTIST && currentUser.getRole() != Role.ADMIN) {
            throw new AppException(
                    "Only verified artists can create products. Submit an artist application to get approved.",
                    HttpStatus.FORBIDDEN);
        }
        return currentUser;
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        User currentUser = requireSellerRole();

        Product.ProductBuilder productBuilder = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrls(request.getImageUrls())
                .videoUrl(request.getVideoUrl())
                .stockQuantity(request.getStockQuantity())
                .category(request.getCategory() != null ? request.getCategory() : "Clothing")
                .sizeStock(request.getSizeStock() != null ? request.getSizeStock() : new java.util.HashMap<>())
                .availableColors(request.getAvailableColors() != null ? request.getAvailableColors() : new java.util.ArrayList<>())
                .status(ProductStatus.PENDING) // All new products start as PENDING
                .productType(request.getProductType() != null ? request.getProductType() : ProductType.REGULAR)
                .seller(currentUser)
                .createdAt(LocalDateTime.now());

        Product product = productBuilder.build();
        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    /**
     * Create a product with file uploads
     * @param request the product create request with file uploads
     * @return the created product response
     */
    @Transactional
    public ProductResponse createProductWithFiles(ProductCreateRequest request) {
        User currentUser = requireSellerRole();

        // Store image files
        List<String> imageUrls = fileStorageService.storeFiles(request.getImages());

        // Store video file if provided
        String videoUrl = null;
        if (request.getVideo() != null && !request.getVideo().isEmpty()) {
            videoUrl = fileStorageService.storeFile(request.getVideo());
        }

        Product.ProductBuilder productBuilder = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrls(imageUrls)
                .videoUrl(videoUrl)
                .stockQuantity(request.getStockQuantity())
                .category(request.getCategory() != null ? request.getCategory() : "Clothing")
                .sizeStock(request.getSizeStock() != null ? request.getSizeStock() : new java.util.HashMap<>())
                .availableColors(request.getAvailableColors() != null ? request.getAvailableColors() : new java.util.ArrayList<>())
                .status(ProductStatus.PENDING) // All new products start as PENDING
                .productType(request.getProductType() != null ? request.getProductType() : ProductType.REGULAR)
                .seller(currentUser)
                .createdAt(LocalDateTime.now());

        // Initial bid price is automatically set to the product price in the Product entity's onCreate method

        Product product = productBuilder.build();
        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProductStatus(Long productId, ProductStatusUpdateRequest request) {
        User currentUser = userService.getCurrentUser();

        // Only admins can update product status
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AppException("Only admins can approve or reject products", HttpStatus.FORBIDDEN);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        product.setStatus(request.getStatus());

        if (request.getStatus() == ProductStatus.APPROVED) {
            product.setApprovedAt(LocalDateTime.now());
            product.setAdminNotes(null); // Clear rejection notes on approval
        } else if (request.getStatus() == ProductStatus.REJECTED) {
            if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
                throw new AppException("Rejection reason is mandatory when rejecting a product submission.", HttpStatus.BAD_REQUEST);
            }
            product.setAdminNotes(request.getRejectionReason().trim());
        }

        Product updatedProduct = productRepository.save(product);
        return mapToProductResponse(updatedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        User currentUser = userService.getCurrentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        if (!product.getSeller().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new AppException("You do not have permission to edit this product", HttpStatus.FORBIDDEN);
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            product.setImageUrls(request.getImageUrls());
        }
        product.setVideoUrl(request.getVideoUrl());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(request.getCategory());
        if (request.getSizeStock() != null) {
            product.setSizeStock(request.getSizeStock());
        }
        if (request.getAvailableColors() != null) {
            product.setAvailableColors(request.getAvailableColors());
        }

        // Reset status to PENDING for admin re-review upon update
        product.setStatus(ProductStatus.PENDING);
        product.setAdminNotes(null); // Clear previous rejection reason

        Product saved = productRepository.save(product);
        return mapToProductResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        User currentUser = null;
        try {
            currentUser = userService.getCurrentUser();
        } catch (Exception e) {
            // Unauthenticated user viewing public products
        }

        List<Product> products;
        // Admins can see all products, regular or unauthenticated users can only see approved products
        if (currentUser != null && currentUser.getRole() == Role.ADMIN) {
            products = productRepository.findAll();
        } else {
            products = productRepository.findByStatus(ProductStatus.APPROVED);
        }

        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getNewDrops() {
        List<Product> products = productRepository.findByStatusOrderByApprovedAtDescCreatedAtDesc(ProductStatus.APPROVED);
        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getPendingProducts() {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AppException("Only admins can view pending products", HttpStatus.FORBIDDEN);
        }
        List<Product> products = productRepository.findByStatus(ProductStatus.PENDING);
        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getMyProducts() {
        User currentUser = userService.getCurrentUser();
        List<Product> products = productRepository.findBySeller(currentUser);

        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        User currentUser = userService.getCurrentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        // Regular users can only see approved products unless they are the seller
        if (currentUser.getRole() != Role.ADMIN && 
            !product.getSeller().getId().equals(currentUser.getId()) && 
            product.getStatus() != ProductStatus.APPROVED) {
            throw new AppException("Product not found", HttpStatus.NOT_FOUND);
        }

        return mapToProductResponse(product);
    }

    /**
     * Move a product to the bidding section
     * @param productId the ID of the product to move
     * @param initialBidPrice the initial bid price
     * @return the updated product
     */
    @Transactional
    public ProductResponse moveProductToBidding(Long productId, BigDecimal initialBidPrice) {
        User currentUser = userService.getCurrentUser();

        // Only admins can move products to bidding
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AppException("Only admins can move products to bidding", HttpStatus.FORBIDDEN);
        }

        if (initialBidPrice == null || initialBidPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException("Initial bid price must be greater than 0", HttpStatus.BAD_REQUEST);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        // Only approved products can be moved to bidding
        if (product.getStatus() != ProductStatus.APPROVED) {
            throw new AppException("Only approved products can be moved to bidding", HttpStatus.BAD_REQUEST);
        }

        // Product must be a regular product
        if (product.getProductType() == ProductType.BIDDING) {
            throw new AppException("Product is already in bidding", HttpStatus.BAD_REQUEST);
        }

        // Move product to bidding
        product.setProductType(ProductType.BIDDING);
        product.setInitialBidPrice(initialBidPrice);

        Product updatedProduct = productRepository.save(product);
        return mapToProductResponse(updatedProduct);
    }

    private ProductResponse mapToProductResponse(Product product) {
        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrls(product.getImageUrls())
                .videoUrl(product.getVideoUrl())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .sizeStock(product.getSizeStock())
                .availableColors(product.getAvailableColors())
                .status(product.getStatus())
                .productType(product.getProductType())
                .sellerId(product.getSeller().getId())
                .sellerName(product.getSeller().getUsername() != null && !product.getSeller().getUsername().trim().isEmpty() ? product.getSeller().getUsername() : product.getSeller().getFullName())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .approvedAt(product.getApprovedAt())
                .adminNotes(product.getAdminNotes())
                .hasPost(false);

        // Check if a post exists for this product
        postRepository.findByProduct(product).ifPresent(post -> {
            builder.hasPost(true);
            builder.postId(post.getId());
        });

        // Add bidding-related fields if it's a bidding product
        if (product.getProductType() == ProductType.BIDDING) {
            builder.initialBidPrice(product.getInitialBidPrice())
                   .currentBidPrice(product.getCurrentBidPrice());

            // Add bidder information if there is a current bidder
            if (product.getCurrentBidder() != null) {
                builder.currentBidderId(product.getCurrentBidder().getId())
                       .currentBidderName(product.getCurrentBidder().getFullName());
            }

            builder.bidStartTime(product.getBidStartTime())
                   .bidEndTime(product.getBidEndTime());

            // Check if bidding is active
            boolean isBiddingActive = product.getBidStartTime() != null && 
                                     (product.getBidEndTime() == null || 
                                      product.getBidEndTime().isAfter(LocalDateTime.now()));
            builder.isBiddingActive(isBiddingActive);
        }

        return builder.build();
    }
}
