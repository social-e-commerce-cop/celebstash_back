package com.celebstash.backend.controller;

import com.celebstash.backend.dto.user.UserProfileUpdateRequest;
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
}
