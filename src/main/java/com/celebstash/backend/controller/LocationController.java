package com.celebstash.backend.controller;

import com.celebstash.backend.dto.location.LocationRequest;
import com.celebstash.backend.dto.location.LocationResponse;
import com.celebstash.backend.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Location management APIs")
public class LocationController {

    private final LocationService locationService;

    @PostMapping
    @Operation(summary = "Create a new location", description = "Creates a new delivery location")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody LocationRequest request) {
        return new ResponseEntity<>(locationService.createLocation(request), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get my locations", description = "Returns all locations for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<LocationResponse>> getMyLocations() {
        return ResponseEntity.ok(locationService.getMyLocations());
    }

    @GetMapping("/active")
    @Operation(summary = "Get my active locations", description = "Returns all active locations for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<LocationResponse>> getMyActiveLocations() {
        return ResponseEntity.ok(locationService.getMyActiveLocations());
    }

    @GetMapping("/all-active")
    @Operation(summary = "Get all active locations", description = "Returns all active locations (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LocationResponse>> getAllActiveLocations() {
        return ResponseEntity.ok(locationService.getAllActiveLocations());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get location by ID", description = "Returns a location by its ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<LocationResponse> getLocationById(@PathVariable("id") Long locationId) {
        return ResponseEntity.ok(locationService.getLocationById(locationId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update location", description = "Updates a location (only the owner can update)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable("id") Long locationId,
            @Valid @RequestBody LocationRequest request) {
        return ResponseEntity.ok(locationService.updateLocation(locationId, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete location", description = "Deletes a location (only the owner can delete)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteLocation(@PathVariable("id") Long locationId) {
        locationService.deleteLocation(locationId);
        return ResponseEntity.noContent().build();
    }
}