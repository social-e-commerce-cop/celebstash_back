package com.celebstash.backend.controller;

import com.celebstash.backend.dto.premium.PremiumContentRequest;
import com.celebstash.backend.dto.premium.PremiumContentResponse;
import com.celebstash.backend.dto.premium.StreamUrlResponse;
import com.celebstash.backend.model.enums.PremiumAccessModel;
import com.celebstash.backend.service.FileStorageService;
import com.celebstash.backend.service.PremiumContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/premium-content")
@RequiredArgsConstructor
@Validated
@Tag(name = "Premium Content", description = "Creator unreleased songs and album media access APIs")
public class PremiumContentController {

    private final PremiumContentService premiumContentService;
    private final FileStorageService fileStorageService;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload premium content", description = "Allows verified creators to upload audio/video content with access details")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PremiumContentResponse> uploadContent(
            @RequestParam("title") @NotBlank String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "coverImageUrl", required = false) String coverImageUrl,
            @RequestParam("accessModel") @NotNull PremiumAccessModel accessModel,
            @RequestParam("price") @NotNull @Min(0) BigDecimal price,
            @RequestParam(value = "earlyAccessUntil", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime earlyAccessUntil,
            @RequestParam("file") MultipartFile file) {

        String fileName = fileStorageService.storeFile(file);

        PremiumContentRequest request = PremiumContentRequest.builder()
                .title(title)
                .description(description)
                .coverImageUrl(coverImageUrl)
                .accessModel(accessModel)
                .price(price)
                .earlyAccessUntil(earlyAccessUntil)
                .build();

        return new ResponseEntity<>(premiumContentService.uploadContent(request, fileName), HttpStatus.CREATED);
    }

    @GetMapping("/creator/{creatorId}")
    @Operation(summary = "Get creator's premium content", description = "Retrieves all unreleased tracks of a specific creator")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PremiumContentResponse>> getCreatorPremiumContent(@PathVariable Long creatorId) {
        return ResponseEntity.ok(premiumContentService.getCreatorPremiumContent(creatorId));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Unlock premium content", description = "Deducts price from wallet and creates unlock pass")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PremiumContentResponse> unlockContent(
            @PathVariable Long id,
            @RequestParam("pin") @NotBlank String pin) {
        return ResponseEntity.ok(premiumContentService.unlockContent(id, pin));
    }

    @GetMapping("/{id}/stream")
    @Operation(summary = "Get signed media stream URL", description = "Generates a signed expiring stream URL for verified unlocked users")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<StreamUrlResponse> getStreamUrl(@PathVariable Long id) {
        return ResponseEntity.ok(premiumContentService.getStreamUrl(id));
    }

    @PostMapping("/sync-plays")
    @Operation(summary = "Sync offline plays count", description = "Decrements remaining plays count for offline synced plays")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> syncPlays(@RequestBody List<com.celebstash.backend.dto.premium.PlaySyncRequest> requests) {
        premiumContentService.syncPlays(requests);
        return ResponseEntity.ok().build();
    }
}
