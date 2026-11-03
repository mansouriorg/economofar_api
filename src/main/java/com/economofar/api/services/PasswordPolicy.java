package com.economofar.api.services;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * The rules a new password must follow, on top of its length in characters (8 to 72, checked by the sign-up).
 * BCrypt reads 72 bytes at most, and an accented letter takes 2 bytes or more, so the length in bytes is checked here.
 * The answer is a message for the user, or null when the password is fine.
 */
final class PasswordPolicy {

    static final String TOO_LONG = "Le mot de passe est trop long (72 octets au maximum, une lettre accentuee en compte 2)";
    static final String NEEDS_LETTER_AND_DIGIT = "Le mot de passe doit contenir au moins une lettre et un chiffre";
    static final String TOO_COMMON = "Ce mot de passe est trop courant, choisissez-en un autre";
    static final String CONTAINS_EMAIL = "Le mot de passe ne doit pas contenir votre adresse email";

    private static final int MAX_BYTES = 72;
    private static final int MIN_LOCAL_PART = 4;

    private static final Set<String> COMMON = Set.of(
            "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd",
            "azerty123", "azerty1234", "azertyuiop1", "qwerty123", "qwerty1234", "qwerty12345",
            "motdepasse1", "motdepasse123", "abc12345", "abcd1234", "abcd12345",
            "iloveyou1", "welcome1", "welcome123", "admin123", "admin1234",
            "letmein123", "monkey123", "dragon123", "football1", "soleil123",
            "bonjour123", "bienvenue1", "changeme1"
    );

    private PasswordPolicy() {
    }

    static String weakness(String password, String email) {
        if (password == null || password.isEmpty()) {
            return NEEDS_LETTER_AND_DIGIT;
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return TOO_LONG;
        }

        boolean letter = password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password.codePoints().anyMatch(Character::isDigit);

        if (letter == false || digit == false) {
            return NEEDS_LETTER_AND_DIGIT;
        }

        String lower = password.toLowerCase(Locale.ROOT);

        if (COMMON.contains(lower)) {
            return TOO_COMMON;
        }

        if (containsEmail(lower, email)) {
            return CONTAINS_EMAIL;
        }

        return null;
    }

    private static boolean containsEmail(String lowerPassword, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String address = email.trim().toLowerCase(Locale.ROOT);

        if (lowerPassword.equals(address)) {
            return true;
        }

        int at = address.indexOf('@');
        String local = at < 0 ? address : address.substring(0, at);

        return local.length() >= MIN_LOCAL_PART && lowerPassword.contains(local);
    }
}
