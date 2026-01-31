package com.economofar.api.services;

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
    private final JwtService jwtService; // ✅ On utilise le vrai service JWT
    private final AuthenticationManager authenticationManager; // ✅ Pour le login

    public AuthenticationResponse register(RegisterRequest request) {
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.STAFF)
                .build();
        
        repository.save(user);
        
        // Génération du vrai token
        var jwtToken = jwtService.generateToken(user);
        
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Cette ligne vérifie si le login/mot de passe sont bons. Sinon, ça lance une erreur.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        // Si on arrive ici, c'est que le login est bon. On cherche l'user.
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow();
        
        // On génère un nouveau token
        var jwtToken = jwtService.generateToken(user);
        
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}