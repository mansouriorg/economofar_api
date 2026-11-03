package com.economofar.api.services;

import com.economofar.api.dtos.PasswordUpdateResponse;
import com.economofar.api.dtos.UpdatePasswordRequest;
import com.economofar.api.models.Role;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UserProfileServicePasswordTest {

    private static final String OLD_PASSWORD = "Old-Passw0rd-42";
    private static final String NEW_PASSWORD = "Tr0ubadour-92";

    private static final class Saves implements InvocationHandler {

        final List<User> saved = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "save":
                    saved.add((User) args[0]);
                    return args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "Saves";
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
    }

    private static final class Encoder implements PasswordEncoder {

        int matchCalls;

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchCalls++;
            return encodedPassword.equals("encoded:" + rawPassword);
        }
    }

    private static final class Fixture {

        final Saves saves = new Saves();
        final Encoder encoder = new Encoder();
        final UserProfileService service;

        Fixture() {
            UserRepository repository = (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    saves
            );

            service = new UserProfileService(repository, encoder);
        }

        User account(String email) {
            return User.builder()
                    .firstName("Jean")
                    .lastName("Dupont")
                    .email(email)
                    .password("encoded:" + OLD_PASSWORD)
                    .role(Role.ROLE_USER)
                    .build();
        }
    }

    private static UpdatePasswordRequest change(String current, String next, String confirm) {
        UpdatePasswordRequest request = new UpdatePasswordRequest();

        request.setCurrentPassword(current);
        request.setNewPassword(next);
        request.setConfirmPassword(confirm);

        return request;
    }

    private static UpdatePasswordRequest change(String current, String next) {
        return change(current, next, next);
    }

    private static ResponseStatusException refused(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    @Test
    @DisplayName("a good change stores the new password encoded and saves the user once")
    void goodChange() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        PasswordUpdateResponse response = fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD));

        assertNotNull(response);
        assertEquals("encoded:" + NEW_PASSWORD, user.getPassword());
        assertEquals(1, fixture.saves.saved.size());
    }

    @Test
    @DisplayName("a missing request or a missing field answers 400")
    void missingFields() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        assertEquals(400, refused(() -> fixture.service.updatePassword(user, null)).getStatusCode().value());
        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(null, NEW_PASSWORD))).getStatusCode().value());
        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change("  ", NEW_PASSWORD))).getStatusCode().value());
        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, null, null))).getStatusCode().value());
        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD, null))).getStatusCode().value());
        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD, " "))).getStatusCode().value());

        assertEquals("encoded:" + OLD_PASSWORD, user.getPassword());
        assertEquals(0, fixture.saves.saved.size());
    }

    @Test
    @DisplayName("a confirmation that does not match answers 400")
    void confirmationMismatch() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x"))).getStatusCode().value());
        assertEquals("encoded:" + OLD_PASSWORD, user.getPassword());
    }

    @Test
    @DisplayName("a new password shorter than 8 characters answers 400")
    void tooShort() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "abcde1"))).getStatusCode().value());
        assertEquals("encoded:" + OLD_PASSWORD, user.getPassword());
    }

    @Test
    @DisplayName("8 characters are enough and 7 are not")
    void lengthBoundary() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "abcdef1"))).getStatusCode().value());

        fixture.service.updatePassword(user, change(OLD_PASSWORD, "abcdefg1"));

        assertEquals("encoded:abcdefg1", user.getPassword());
    }

    @Test
    @DisplayName("the rules of the sign-up apply to the new password too, with the same messages")
    void sameRulesAsSignUp() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        ResponseStatusException noDigit = refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "abcdefghij")));
        ResponseStatusException noLetter = refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "1234567890")));
        ResponseStatusException common = refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "Password123")));
        ResponseStatusException email = refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "jean.dupont2024")));
        ResponseStatusException tooLong = refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, "a".repeat(72) + "1")));

        assertEquals(400, noDigit.getStatusCode().value());
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, noDigit.getReason());
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, noLetter.getReason());
        assertEquals(PasswordPolicy.TOO_COMMON, common.getReason());
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, email.getReason());
        assertEquals(PasswordPolicy.TOO_LONG, tooLong.getReason());
        assertEquals("encoded:" + OLD_PASSWORD, user.getPassword());
        assertEquals(0, fixture.saves.saved.size());
    }

    @Test
    @DisplayName("the e-mail rule uses the e-mail of the account")
    void emailRuleUsesTheAccount() {
        Fixture fixture = new Fixture();
        User user = fixture.account("marie.martin@mail.com");

        fixture.service.updatePassword(user, change(OLD_PASSWORD, "jean.dupont2024"));

        assertEquals("encoded:jean.dupont2024", user.getPassword());
    }

    @Test
    @DisplayName("a wrong current password answers 400 and changes nothing")
    void wrongCurrentPassword() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change("not-my-password1", NEW_PASSWORD))).getStatusCode().value());
        assertEquals("encoded:" + OLD_PASSWORD, user.getPassword());
        assertEquals(0, fixture.saves.saved.size());
    }

    @Test
    @DisplayName("a new password equal to the current one answers 400")
    void sameAsCurrent() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        assertEquals(400, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, OLD_PASSWORD))).getStatusCode().value());
        assertEquals(0, fixture.saves.saved.size());
    }

    @Test
    @DisplayName("five wrong current passwords block the change, even with the right one afterwards")
    void lockAfterFiveWrongPasswords() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertEquals(400, refused(() -> fixture.service.updatePassword(user, change("not-my-password1", NEW_PASSWORD))).getStatusCode().value());
        }

        int checks = fixture.encoder.matchCalls;

        assertEquals(429, refused(() -> fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD))).getStatusCode().value());
        assertEquals(checks, fixture.encoder.matchCalls);
        assertEquals("encoded:" + OLD_PASSWORD, user.getPassword());
        assertEquals(0, fixture.saves.saved.size());
    }

    @Test
    @DisplayName("four wrong current passwords do not block the change")
    void fourWrongPasswordsAreFine() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertEquals(400, refused(() -> fixture.service.updatePassword(user, change("not-my-password1", NEW_PASSWORD))).getStatusCode().value());
        }

        fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD));

        assertEquals("encoded:" + NEW_PASSWORD, user.getPassword());
    }

    @Test
    @DisplayName("a good change clears the count of wrong passwords")
    void successClearsTheCount() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        for (int attempt = 1; attempt <= 4; attempt++) {
            refused(() -> fixture.service.updatePassword(user, change("not-my-password1", NEW_PASSWORD)));
        }

        fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertEquals(400, refused(() -> fixture.service.updatePassword(user, change("not-my-password1", "An0ther-pass-77"))).getStatusCode().value());
        }

        fixture.service.updatePassword(user, change(NEW_PASSWORD, "An0ther-pass-77"));

        assertEquals("encoded:An0ther-pass-77", user.getPassword());
    }

    @Test
    @DisplayName("the lock is per account and ignores the case of the e-mail")
    void lockIsPerAccount() {
        Fixture fixture = new Fixture();
        User jean = fixture.account("jean.dupont@mail.com");
        User sameJean = fixture.account("JEAN.Dupont@Mail.com");
        User marie = fixture.account("marie.martin@mail.com");

        for (int attempt = 1; attempt <= 5; attempt++) {
            refused(() -> fixture.service.updatePassword(jean, change("not-my-password1", NEW_PASSWORD)));
        }

        assertEquals(429, refused(() -> fixture.service.updatePassword(sameJean, change(OLD_PASSWORD, NEW_PASSWORD))).getStatusCode().value());

        fixture.service.updatePassword(marie, change(OLD_PASSWORD, NEW_PASSWORD));

        assertEquals("encoded:" + NEW_PASSWORD, marie.getPassword());
    }

    @Test
    @DisplayName("a request refused before the current password is checked is not counted")
    void earlyRefusalsAreNotCounted() {
        Fixture fixture = new Fixture();
        User user = fixture.account("jean.dupont@mail.com");

        for (int attempt = 1; attempt <= 10; attempt++) {
            refused(() -> fixture.service.updatePassword(user, change("not-my-password1", "abcdefghij")));
            refused(() -> fixture.service.updatePassword(user, change("not-my-password1", NEW_PASSWORD, "other")));
        }

        assertEquals(0, fixture.encoder.matchCalls);

        fixture.service.updatePassword(user, change(OLD_PASSWORD, NEW_PASSWORD));

        assertTrue(user.getPassword().endsWith(NEW_PASSWORD));
    }
}
