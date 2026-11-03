package com.economofar.api.services;

import com.economofar.api.dtos.PasswordUpdateResponse;
import com.economofar.api.dtos.UpdatePasswordRequest;
import com.economofar.api.dtos.UpdateUserProfileRequest;
import com.economofar.api.dtos.UserProfileResponse;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttempts passwordAttempts = new LoginAttempts();

    public UserProfileService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfileResponse getProfile(User user) {
        ensureDefaultPreferences(user);
        return UserProfileResponse.fromUser(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(User user, UpdateUserProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        boolean emailChanged = false;

        if (request.getFirstName() != null) {
            String firstName = clean(request.getFirstName());

            if (firstName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name cannot be empty");
            }

            if (firstName.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name is too long");
            }

            user.setFirstName(firstName);
        }

        if (request.getLastName() != null) {
            String lastName = clean(request.getLastName());

            if (lastName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last name cannot be empty");
            }

            if (lastName.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last name is too long");
            }

            user.setLastName(lastName);
        }

        if (request.getEmail() != null) {
            String email = clean(request.getEmail());

            if (email == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
            }

            email = email.toLowerCase(Locale.ROOT);

            if (!isValidEmail(email)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
            }

            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCase(email)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This email is already used by another account");
                }

                user.setEmail(email);
                emailChanged = true;
            }
        }

        if (request.getCompanyName() != null) {
            String companyName = clean(request.getCompanyName());

            if (companyName != null && companyName.length() > 150) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name is too long");
            }

            user.setCompanyName(companyName);
        }

        if (request.getPreferredCurrency() != null) {
            String currency = clean(request.getPreferredCurrency());

            if (currency == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferred currency cannot be empty");
            }

            currency = currency.toUpperCase(Locale.ROOT);

            if (!isValidCurrency(currency)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency. Use a 3-letter currency code like EUR, USD, GBP");
            }

            user.setPreferredCurrency(currency);
        }

        if (request.getLanguage() != null) {
            String language = clean(request.getLanguage());

            if (language == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language cannot be empty");
            }

            language = language.toLowerCase(Locale.ROOT);

            if (!isValidLanguage(language)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid language. Supported values are fr, en, ar");
            }

            user.setLanguage(language);
        }

        ensureDefaultPreferences(user);

        User savedUser = userRepository.save(user);

        return UserProfileResponse.fromUser(savedUser, emailChanged);
    }

    @Transactional
    public PasswordUpdateResponse updatePassword(User user, UpdatePasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        if (request.getConfirmPassword() == null || request.getConfirmPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password confirmation is required");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirmation do not match");
        }

        if (request.getNewPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters");
        }

        String passwordProblem = PasswordPolicy.weakness(request.getNewPassword(), user.getEmail());

        if (passwordProblem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, passwordProblem);
        }

        String attemptKey = String.valueOf(user.getEmail()).toLowerCase(Locale.ROOT);

        if (passwordAttempts.isLocked(attemptKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong passwords. Try again in a few minutes.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            passwordAttempts.recordFailure(attemptKey);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        passwordAttempts.clear(attemptKey);

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return new PasswordUpdateResponse(
                "Password updated successfully. Please log in again.",
                true
        );
    }

    private void ensureDefaultPreferences(User user) {
        if (user.getPreferredCurrency() == null || user.getPreferredCurrency().isBlank()) {
            user.setPreferredCurrency("EUR");
        }

        if (user.getLanguage() == null || user.getLanguage().isBlank()) {
            user.setLanguage("fr");
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private boolean isValidCurrency(String currency) {
        return currency.matches("^[A-Z]{3}$");
    }

    private boolean isValidLanguage(String language) {
        return language.equals("fr")
                || language.equals("en")
                || language.equals("ar");
    }
}