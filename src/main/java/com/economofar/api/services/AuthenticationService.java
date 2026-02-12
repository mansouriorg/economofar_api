package com.economofar.api.services;

import com.economofar.api.config.JwtService;
import com.economofar.api.dtos.AuthenticationRequest;
import com.economofar.api.dtos.AuthenticationResponse;
import com.economofar.api.dtos.RegisterRequest;
import com.economofar.api.models.Role;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; 
    private final AuthenticationManager authenticationManager;

    /**
     * Inscription d'un nouvel utilisateur
     */
    public AuthenticationResponse register(RegisterRequest request) {
        // 1. On construit l'objet User avec le Builder
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) 
                // ✅ Utilisation du nouveau rôle synchronisé avec PostgreSQL
                .role(Role.ROLE_USER) 
                .build();
        
        // 2. On sauvegarde en base de données
        // ⚠️ Si tu n'as pas encore fait le DROP CONSTRAINT dans SQL, cela risque de bloquer ici.
        repository.save(user);
        
        // 3. On génère le Token
        var jwtToken = jwtService.generateToken(user);
        
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    /**
     * Connexion d'un utilisateur existant
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // 1. Authentification via Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        // 2. Récupération de l'utilisateur
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(); 
        
        // 3. Génération du Token
        var jwtToken = jwtService.generateToken(user);
        
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}