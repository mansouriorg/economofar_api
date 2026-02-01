package com.economofar.api.controllers;

import com.economofar.api.dtos.AuthenticationRequest;
import com.economofar.api.dtos.AuthenticationResponse;
import com.economofar.api.dtos.RegisterRequest;
import com.economofar.api.services.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;

    /**
     * Endpoint d'inscription (Register)
     * URL : POST http://localhost:8080/api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request
    ) {
        // Appelle le service pour créer l'utilisateur et générer le token
        return ResponseEntity.ok(service.register(request));
    }

    /**
     * Endpoint de connexion (Login)
     * URL : POST http://localhost:8080/api/auth/authenticate
     */
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request
    ) {
        // Appelle le service pour vérifier le mot de passe et récupérer un token
        return ResponseEntity.ok(service.authenticate(request));
    }
}