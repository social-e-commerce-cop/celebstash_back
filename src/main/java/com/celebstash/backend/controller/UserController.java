package com.celebstash.backend.controller;

import com.celebstash.backend.dto.user.UserProfileUpdateRequest;
import com.celebstash.backend.dto.user.UserPublicDTO;
import com.celebstash.backend.model.User;
import com.celebstash.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and management APIs")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get all users", description = "Returns a list of all registered users (admin access)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<java.util.List<UserPublicDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Returns the currently authenticated user's profile details")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<User> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    @PutMapping("/me")
    @Operation(summary = "Update profile", description = "Updates profile information of the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<User> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Returns the public profile of any user by their ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserPublicDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/by-username/{username}")
    @Operation(summary = "Get user by username", description = "Returns the public profile of any user by their username")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserPublicDTO> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users for tagging", description = "Returns users matching query string")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<java.util.List<UserPublicDTO>> searchUsers(@RequestParam(required = false, defaultValue = "") String query) {
        return ResponseEntity.ok(userService.searchUsers(query));
    }

    @GetMapping("/check-username")
    @Operation(summary = "Check username availability", description = "Checks if a given username is available or already taken")
    public ResponseEntity<java.util.Map<String, Object>> checkUsername(@RequestParam String username) {
        boolean taken = userService.existsByUsername(username.trim());
        return ResponseEntity.ok(java.util.Map.of(
            "username", username,
            "available", !taken
        ));
    }
}
