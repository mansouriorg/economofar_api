package com.economofar.api.services;

import com.economofar.api.config.JwtService; // ✅ IMPORTANT : Importe le bon JwtService (celui de config)
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
    private final JwtService jwtService; // Le service qui crée les tokens cryptés
    private final AuthenticationManager authenticationManager; // Le gestionnaire de login de Spring

    /**
     * Inscription d'un nouvel utilisateur
     */
    public AuthenticationResponse register(RegisterRequest request) {
        // 1. On construit l'objet User avec le Builder
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // 🔒 On crypte le mot de passe ici !
                .role(Role.USER) // Par défaut, on crée un simple utilisateur
                .build();
        
        // 2. On sauvegarde en base de données
        repository.save(user);
        
        // 3. On génère tout de suite son Token pour qu'il soit connecté directement
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
        // Si le mot de passe est faux, cette méthode va lancer une exception et stopper le code.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        // 2. Si on arrive ici, c'est que le mot de passe est bon. On récupère l'user en BDD.
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(); // (Devrait ne jamais arriver car authenticate a réussi)
        
        // 3. On génère un nouveau Token frais
        var jwtToken = jwtService.generateToken(user);
        
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}