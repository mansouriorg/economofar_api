package com.economofar.api.controllers;

import com.economofar.api.dtos.UpdateNotificationPreferencesRequest;
import com.economofar.api.models.AppNotification;
import com.economofar.api.models.NotificationPreference;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import com.economofar.api.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationService notificationService,
            UserRepository userRepository
    ) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreference> getPreferences(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(notificationService.getOrCreatePreferences(user));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<NotificationPreference> updatePreferences(
            Authentication authentication,
            @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(notificationService.updatePreferences(user, request));
    }

    @GetMapping
    public ResponseEntity<List<AppNotification>> getNotifications(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(notificationService.getNotifications(user));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<AppNotification> markAsRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(notificationService.markAsRead(user, id));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        String email = authentication.getName();

        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}