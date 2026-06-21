package com.economofar.api.controllers;

import com.economofar.api.dtos.*;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import com.economofar.api.services.SupportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;
    private final UserRepository userRepository;

    public SupportController(
            SupportService supportService,
            UserRepository userRepository
    ) {
        this.supportService = supportService;
        this.userRepository = userRepository;
    }

    @GetMapping("/faqs")
    public ResponseEntity<List<FaqItemResponse>> getFaqs(
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(supportService.getFaqs(category));
    }

    @GetMapping("/config")
    public ResponseEntity<SupportConfigResponse> getSupportConfig() {
        return ResponseEntity.ok(supportService.getSupportConfig());
    }

    @GetMapping("/status")
    public ResponseEntity<AppStatusResponse> getAppStatus() {
        return ResponseEntity.ok(supportService.getAppStatus());
    }

    @PostMapping("/contact")
    public ResponseEntity<SupportRequestResponse> submitContactSupport(
            Authentication authentication,
            @RequestBody SubmitSupportRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(supportService.submitContactSupport(user, request));
    }

    @PostMapping("/bug-report")
    public ResponseEntity<SupportRequestResponse> reportBug(
            Authentication authentication,
            @RequestBody SubmitSupportRequest request
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(supportService.reportBug(user, request));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<SupportRequestResponse>> getMyRequests(
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(supportService.getMyRequests(user));
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