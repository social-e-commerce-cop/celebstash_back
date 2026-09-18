package com.celebstash.backend.service;

import com.celebstash.backend.dto.location.LocationRequest;
import com.celebstash.backend.dto.location.LocationResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Location;
import com.celebstash.backend.model.User;
import com.celebstash.backend.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final UserService userService;

    /**
     * Create a new location
     * @param request the location request
     * @return the created location response
     */
    @Transactional
    public LocationResponse createLocation(LocationRequest request) {
        User currentUser = userService.getCurrentUser();
        
        Location location = Location.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .deliveryFee(request.getDeliveryFee())
                .active(request.getActive() != null ? request.getActive() : true)
                .user(currentUser)
                .createdAt(LocalDateTime.now())
                .build();
        
        Location savedLocation = locationRepository.save(location);
        return mapToLocationResponse(savedLocation);
    }

    /**
     * Get all locations for the current user
     * @return list of location responses
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getMyLocations() {
        User currentUser = userService.getCurrentUser();
        List<Location> locations = locationRepository.findByUser(currentUser);
        
        return locations.stream()
                .map(this::mapToLocationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all active locations for the current user
     * @return list of active location responses
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getMyActiveLocations() {
        User currentUser = userService.getCurrentUser();
        List<Location> locations = locationRepository.findByUserAndActiveTrue(currentUser);
        
        return locations.stream()
                .map(this::mapToLocationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all active locations (admin only)
     * @return list of active location responses
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getAllActiveLocations() {
        List<Location> locations = locationRepository.findByActiveTrue();
        
        return locations.stream()
                .map(this::mapToLocationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a location by ID
     * @param locationId the location ID
     * @return the location response
     */
    @Transactional(readOnly = true)
    public LocationResponse getLocationById(Long locationId) {
        User currentUser = userService.getCurrentUser();
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException("Location not found", HttpStatus.NOT_FOUND));
        
        // Check if the location belongs to the current user
        if (!location.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("You can only view your own locations", HttpStatus.FORBIDDEN);
        }
        
        return mapToLocationResponse(location);
    }

    /**
     * Update a location
     * @param locationId the location ID
     * @param request the location request
     * @return the updated location response
     */
    @Transactional
    public LocationResponse updateLocation(Long locationId, LocationRequest request) {
        User currentUser = userService.getCurrentUser();
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException("Location not found", HttpStatus.NOT_FOUND));
        
        // Check if the location belongs to the current user
        if (!location.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("You can only update your own locations", HttpStatus.FORBIDDEN);
        }
        
        // Update the location
        location.setName(request.getName());
        location.setAddress(request.getAddress());
        location.setCity(request.getCity());
        location.setState(request.getState());
        location.setZipCode(request.getZipCode());
        location.setCountry(request.getCountry());
        location.setDeliveryFee(request.getDeliveryFee());
        if (request.getActive() != null) {
            location.setActive(request.getActive());
        }
        location.setUpdatedAt(LocalDateTime.now());
        
        Location updatedLocation = locationRepository.save(location);
        return mapToLocationResponse(updatedLocation);
    }

    /**
     * Delete a location
     * @param locationId the location ID
     */
    @Transactional
    public void deleteLocation(Long locationId) {
        User currentUser = userService.getCurrentUser();
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException("Location not found", HttpStatus.NOT_FOUND));
        
        // Check if the location belongs to the current user
        if (!location.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("You can only delete your own locations", HttpStatus.FORBIDDEN);
        }
        
        locationRepository.delete(location);
    }

    /**
     * Map a Location entity to a LocationResponse DTO
     * @param location the location entity
     * @return the location response DTO
     */
    private LocationResponse mapToLocationResponse(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .city(location.getCity())
                .state(location.getState())
                .zipCode(location.getZipCode())
                .country(location.getCountry())
                .deliveryFee(location.getDeliveryFee())
                .active(location.getActive())
                .userId(location.getUser().getId())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}