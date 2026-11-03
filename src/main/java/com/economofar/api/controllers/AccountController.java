package com.economofar.api.controllers;

import com.economofar.api.dtos.DeleteAccountRequest;
import com.economofar.api.models.User;
import com.economofar.api.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * POST /api/account/delete   { "password": "..." }
     *
     * Permanently deletes the authenticated account and all its data.
     */
    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        accountService.deleteAccount(user, request != null ? request.getPassword() : null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("message", "Votre compte et vos données ont été supprimés.");

        return ResponseEntity.ok(body);
    }
}
