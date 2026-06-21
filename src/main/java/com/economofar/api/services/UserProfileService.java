package com.economofar.api.services;

import com.economofar.api.dtos.PasswordUpdateResponse;
import com.economofar.api.dtos.UpdatePasswordRequest;
import com.economofar.api.dtos.UpdateUserProfileRequest;
import com.economofar.api.dtos.UserProfileResponse;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
            throw new RuntimeException("Request body is required");
        }

        boolean emailChanged = false;

        if (request.getFirstName() != null) {
            String firstName = clean(request.getFirstName());

            if (firstName == null) {
                throw new RuntimeException("First name cannot be empty");
            }

            if (firstName.length() > 100) {
                throw new RuntimeException("First name is too long");
            }

            user.setFirstName(firstName);
        }

        if (request.getLastName() != null) {
            String lastName = clean(request.getLastName());

            if (lastName == null) {
                throw new RuntimeException("Last name cannot be empty");
            }

            if (lastName.length() > 100) {
                throw new RuntimeException("Last name is too long");
            }

            user.setLastName(lastName);
        }

        if (request.getEmail() != null) {
            String email = clean(request.getEmail());

            if (email == null) {
                throw new RuntimeException("Email cannot be empty");
            }

            email = email.toLowerCase(Locale.ROOT);

            if (!isValidEmail(email)) {
                throw new RuntimeException("Invalid email format");
            }

            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmail(email)) {
                    throw new RuntimeException("This email is already used by another account");
                }

                user.setEmail(email);
                emailChanged = true;
            }
        }

        if (request.getCompanyName() != null) {
            String companyName = clean(request.getCompanyName());

            if (companyName != null && companyName.length() > 150) {
                throw new RuntimeException("Company name is too long");
            }

            user.setCompanyName(companyName);
        }

        if (request.getPreferredCurrency() != null) {
            String currency = clean(request.getPreferredCurrency());

            if (currency == null) {
                throw new RuntimeException("Preferred currency cannot be empty");
            }

            currency = currency.toUpperCase(Locale.ROOT);

            if (!isValidCurrency(currency)) {
                throw new RuntimeException("Invalid currency. Use a 3-letter currency code like EUR, USD, GBP");
            }

            user.setPreferredCurrency(currency);
        }

        if (request.getLanguage() != null) {
            String language = clean(request.getLanguage());

            if (language == null) {
                throw new RuntimeException("Language cannot be empty");
            }

            language = language.toLowerCase(Locale.ROOT);

            if (!isValidLanguage(language)) {
                throw new RuntimeException("Invalid language. Supported values are fr, en, ar");
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
            throw new RuntimeException("Request body is required");
        }

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new RuntimeException("Current password is required");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("New password is required");
        }

        if (request.getConfirmPassword() == null || request.getConfirmPassword().isBlank()) {
            throw new RuntimeException("Password confirmation is required");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirmation do not match");
        }

        if (request.getNewPassword().length() < 8) {
            throw new RuntimeException("Password must contain at least 8 characters");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("New password must be different from the current password");
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