package com.economofar.api.services;

import com.economofar.api.dtos.AuthenticationResponse;
import com.economofar.api.dtos.RegisterRequest;
import com.economofar.api.models.Role;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationResponse register(RegisterRequest request) {
        // 1. Créer l'objet utilisateur
        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // On crypte le mot de passe !
                .role(Role.STAFF) // Par défaut, tout le monde est STAFF
                .build();

        // 2. Sauvegarder en base de données
        repository.save(user);

        // 3. Retourner une réponse (Token fictif pour l'instant)
        return AuthenticationResponse.builder()
                .token("TOKEN_A_VENIR")
                .build();
    }
}