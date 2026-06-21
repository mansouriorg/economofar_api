package com.economofar.api.controllers;

import com.economofar.api.dtos.PasswordUpdateResponse;
import com.economofar.api.dtos.UpdatePasswordRequest;
import com.economofar.api.dtos.UpdateUserProfileRequest;
import com.economofar.api.dtos.UserProfileResponse;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import com.economofar.api.services.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final UserRepository userRepository;

    public UserProfileController(
            UserProfileService userProfileService,
            UserRepository userRepository
    ) {
        this.userProfileService = userProfileService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(userProfileService.getProfile(user));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @RequestBody UpdateUserProfileRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(userProfileService.updateProfile(user, request));
    }

    @PutMapping("/password")
    public ResponseEntity<PasswordUpdateResponse> updatePassword(
            Authentication authentication,
            @RequestBody UpdatePasswordRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(userProfileService.updatePassword(user, request));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated request");
        }

        String email = authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}