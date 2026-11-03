package com.economofar.api.services;

import com.economofar.api.config.JwtService;
import com.economofar.api.dtos.AuthenticationRequest;
import com.economofar.api.dtos.AuthenticationResponse;
import com.economofar.api.dtos.RegisterRequest;
import com.economofar.api.models.Role;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Failed login attempts per email (in memory, reset after 15 minutes).
     */
    private final LoginAttempts loginAttempts = new LoginAttempts();

    /**
     * Register a new user.
     */
    public AuthenticationResponse register(RegisterRequest request) {
        if (request == null) {
            throw badRequest("Requête d'inscription invalide");
        }

        String firstName = clean(request.getFirstName());
        String lastName = clean(request.getLastName());
        String email = clean(request.getEmail());
        String password = request.getPassword();

        if (firstName == null) {
            throw badRequest("Le prénom est obligatoire");
        }

        if (lastName == null) {
            throw badRequest("Le nom est obligatoire");
        }

        if (firstName.length() > 100 || lastName.length() > 100) {
            throw badRequest("Le prénom ou le nom est trop long");
        }

        if (email == null) {
            throw badRequest("L'adresse email est obligatoire");
        }

        email = email.toLowerCase(Locale.ROOT);

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("Adresse email invalide");
        }

        if (password == null || password.length() < 8) {
            throw badRequest("Le mot de passe doit contenir au moins 8 caractères");
        }

        if (password.length() > 72) {
            throw badRequest("Le mot de passe est trop long (72 caractères maximum)");
        }

        String passwordProblem = PasswordPolicy.weakness(password, email);

        if (passwordProblem != null) {
            throw badRequest(passwordProblem);
        }

        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un compte existe déjà avec cet email"
            );
        }

        var user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.ROLE_USER)
                .build();

        repository.save(user);

        var jwtToken = jwtService.generateToken(user);

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    /**
     * Login an existing user.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        if (request == null
                || request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isEmpty()) {
            throw badRequest("L'email et le mot de passe sont obligatoires");
        }

        String email = request.getEmail().trim();
        String attemptKey = email.toLowerCase(Locale.ROOT);

        assertNotLocked(attemptKey);

        // Exact match first (old accounts), then lower-case (new accounts are stored in lower-case)
        User user = repository.findByEmail(email)
                .or(() -> repository.findByEmail(attemptKey))
                .orElse(null);

        if (user == null) {
            recordFailure(attemptKey);
            throw new BadCredentialsException("Invalid credentials");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException exception) {
            recordFailure(attemptKey);
            throw exception;
        }

        loginAttempts.clear(attemptKey);

        var jwtToken = jwtService.generateToken(user);

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    private void assertNotLocked(String key) {
        if (loginAttempts.isLocked(key)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives de connexion. Réessayez dans quelques minutes."
            );
        }
    }

    private void recordFailure(String key) {
        loginAttempts.recordFailure(key);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
