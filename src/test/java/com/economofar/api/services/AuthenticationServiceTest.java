package com.economofar.api.services;

import com.economofar.api.config.JwtService;
import com.economofar.api.dtos.AuthenticationRequest;
import com.economofar.api.dtos.AuthenticationResponse;
import com.economofar.api.dtos.RegisterRequest;
import com.economofar.api.models.Role;
import com.economofar.api.models.User;
import com.economofar.api.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class AuthenticationServiceTest {

    private static final String GOOD_PASSWORD = "Tr0ubadour-92";

    private static final class Users implements InvocationHandler {

        final Map<String, User> byEmail = new HashMap<>();
        final Set<String> takenLowerCase = new HashSet<>();
        final List<User> saved = new ArrayList<>();
        final List<String> lookups = new ArrayList<>();
        final List<String> existenceChecks = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "findByEmail":
                    lookups.add((String) args[0]);
                    return Optional.ofNullable(byEmail.get((String) args[0]));
                case "existsByEmailIgnoreCase":
                    existenceChecks.add((String) args[0]);
                    return takenLowerCase.contains(((String) args[0]).toLowerCase());
                case "save":
                    saved.add((User) args[0]);
                    return args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "Users";
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
    }

    private static final class Encoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded:" + rawPassword);
        }
    }

    private static final class Manager implements AuthenticationManager {

        final Map<String, String> passwords = new HashMap<>();
        int calls;

        @Override
        public Authentication authenticate(Authentication authentication) {
            calls++;

            String expected = passwords.get(String.valueOf(authentication.getPrincipal()));

            if (expected != null && expected.equals(String.valueOf(authentication.getCredentials()))) {
                return authentication;
            }

            throw new BadCredentialsException("Bad credentials");
        }
    }

    private static final class Fixture {

        final Users users = new Users();
        final Manager manager = new Manager();
        final JwtService jwt = new JwtService(secret(), 1);
        final AuthenticationService service;

        Fixture() {
            UserRepository repository = (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    users
            );

            service = new AuthenticationService(repository, new Encoder(), jwt, manager);
        }

        User addAccount(String storedEmail, String password) {
            User user = User.builder()
                    .firstName("Jean")
                    .lastName("Dupont")
                    .email(storedEmail)
                    .password("encoded:" + password)
                    .role(Role.ROLE_USER)
                    .build();

            users.byEmail.put(storedEmail, user);
            users.takenLowerCase.add(storedEmail.toLowerCase());
            manager.passwords.put(storedEmail, password);

            return user;
        }
    }

    private static String secret() {
        byte[] bytes = new byte[48];

        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index + 7);
        }

        return Base64.getEncoder().encodeToString(bytes);
    }

    private static RegisterRequest signUp(String first, String last, String email, String password) {
        return RegisterRequest.builder().firstName(first).lastName(last).email(email).password(password).build();
    }

    private static RegisterRequest goodSignUp() {
        return signUp("Jean", "Dupont", "jean.dupont@mail.com", GOOD_PASSWORD);
    }

    private static AuthenticationRequest signIn(String email, String password) {
        return AuthenticationRequest.builder().email(email).password(password).build();
    }

    private static int statusOf(Runnable action) {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class, action::run);
        return refused.getStatusCode().value();
    }

    @Test
    @DisplayName("a sign-up stores a clean user and returns a token that works")
    void signUpWorks() {
        Fixture fixture = new Fixture();

        AuthenticationResponse response = fixture.service.register(signUp("  Jean ", " Dupont  ", "  Jean.Dupont@Mail.COM ", GOOD_PASSWORD));

        assertEquals(1, fixture.users.saved.size());

        User saved = fixture.users.saved.get(0);

        assertEquals("Jean", saved.getFirstName());
        assertEquals("Dupont", saved.getLastName());
        assertEquals("jean.dupont@mail.com", saved.getEmail());
        assertEquals(Role.ROLE_USER, saved.getRole());
        assertNotNull(response.getToken());
        assertEquals("jean.dupont@mail.com", fixture.jwt.extractUsername(response.getToken()));
    }

    @Test
    @DisplayName("the password is stored encoded and never in clear")
    void passwordIsEncoded() {
        Fixture fixture = new Fixture();

        fixture.service.register(goodSignUp());

        String stored = fixture.users.saved.get(0).getPassword();

        assertEquals("encoded:" + GOOD_PASSWORD, stored);
        assertNotEquals(GOOD_PASSWORD, stored);
    }

    @Test
    @DisplayName("the e-mail is checked for an existing account in lower case")
    void existenceCheckUsesLowerCase() {
        Fixture fixture = new Fixture();

        fixture.service.register(signUp("Jean", "Dupont", " JEAN.Dupont@Mail.com ", GOOD_PASSWORD));

        assertEquals(List.of("jean.dupont@mail.com"), fixture.users.existenceChecks);
    }

    @Test
    @DisplayName("an e-mail already used answers 409, whatever its case, and nothing is stored")
    void duplicateEmail() {
        Fixture fixture = new Fixture();

        fixture.users.takenLowerCase.add("jean.dupont@mail.com");

        assertEquals(409, statusOf(() -> fixture.service.register(goodSignUp())));
        assertEquals(409, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", "JEAN.DUPONT@MAIL.COM", GOOD_PASSWORD))));
        assertEquals(0, fixture.users.saved.size());
    }

    @Test
    @DisplayName("a missing request answers 400")
    void missingRequest() {
        Fixture fixture = new Fixture();

        assertEquals(400, statusOf(() -> fixture.service.register(null)));
    }

    @Test
    @DisplayName("a missing or blank first name or last name answers 400")
    void namesAreRequired() {
        Fixture fixture = new Fixture();

        for (String empty : new String[]{null, "", "   "}) {
            assertEquals(400, statusOf(() -> fixture.service.register(signUp(empty, "Dupont", "jean@mail.com", GOOD_PASSWORD))));
            assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", empty, "jean@mail.com", GOOD_PASSWORD))));
        }

        assertEquals(0, fixture.users.saved.size());
    }

    @Test
    @DisplayName("a name of 100 characters is accepted and 101 answers 400")
    void nameLength() {
        Fixture fixture = new Fixture();
        String hundred = "a".repeat(100);
        String more = "a".repeat(101);

        fixture.service.register(signUp(hundred, hundred, "long.names@mail.com", GOOD_PASSWORD));
        assertEquals(1, fixture.users.saved.size());

        assertEquals(400, statusOf(() -> fixture.service.register(signUp(more, "Dupont", "jean@mail.com", GOOD_PASSWORD))));
        assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", more, "jean@mail.com", GOOD_PASSWORD))));
        assertEquals(1, fixture.users.saved.size());
    }

    @Test
    @DisplayName("a missing or blank e-mail answers 400")
    void emailIsRequired() {
        Fixture fixture = new Fixture();

        for (String empty : new String[]{null, "", "    "}) {
            assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", empty, GOOD_PASSWORD))));
        }

        assertEquals(0, fixture.users.saved.size());
    }

    @Test
    @DisplayName("an e-mail that is not an address answers 400")
    void invalidEmails() {
        Fixture fixture = new Fixture();
        String[] invalid = {"no-at-sign", "a@b", "a@b.c", "@mail.com", "jean@.com", "jean dupont@mail.com", "jean@mail..", "jean@mail.com.", "jean@@mail.com", "jean@mail.c0m"};

        for (String email : invalid) {
            assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", email, GOOD_PASSWORD))), email);
        }

        assertEquals(0, fixture.users.saved.size());
    }

    @Test
    @DisplayName("usual e-mail addresses are accepted")
    void validEmails() {
        Fixture fixture = new Fixture();
        String[] valid = {"jean@mail.fr", "jean.dupont+shop@mail.example.fr", "j_d-1@sub.domain.org", "JEAN@MAIL.COM"};

        for (String email : valid) {
            fixture.service.register(signUp("Jean", "Dupont", email, GOOD_PASSWORD));
        }

        assertEquals(valid.length, fixture.users.saved.size());
        assertEquals("jean@mail.com", fixture.users.saved.get(3).getEmail());
    }

    @Test
    @DisplayName("a password of 8 to 72 characters is accepted, shorter or longer answers 400")
    void passwordLength() {
        Fixture fixture = new Fixture();
        String seven = "abcdef1";
        String eight = "abcdefg1";
        String seventyTwo = "a".repeat(71) + "1";
        String seventyThree = "a".repeat(72) + "1";

        assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", "one@mail.com", seven))));
        assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", "two@mail.com", seventyThree))));
        assertEquals(0, fixture.users.saved.size());

        fixture.service.register(signUp("Jean", "Dupont", "three@mail.com", eight));
        fixture.service.register(signUp("Jean", "Dupont", "four@mail.com", seventyTwo));
        assertEquals(2, fixture.users.saved.size());
    }

    @Test
    @DisplayName("a missing password answers 400")
    void missingPassword() {
        Fixture fixture = new Fixture();

        assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", "jean@mail.com", null))));
        assertEquals(0, fixture.users.saved.size());
    }

    @Test
    @DisplayName("a password without a letter, without a digit, too common or built on the e-mail answers 400")
    void passwordRules() {
        Fixture fixture = new Fixture();
        String[] weak = {"abcdefghij", "1234567890", "Password123", "azerty1234", "jean.dupont2024"};

        for (String password : weak) {
            assertEquals(400, statusOf(() -> fixture.service.register(signUp("Jean", "Dupont", "jean.dupont@mail.com", password))), password);
        }

        assertEquals(0, fixture.users.saved.size());
    }

    @Test
    @DisplayName("the reason of a weak password is given to the user")
    void passwordReasonIsGiven() {
        Fixture fixture = new Fixture();

        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> fixture.service.register(signUp("Jean", "Dupont", "jean@mail.com", "abcdefghij")));

        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, refused.getReason());

        refused = assertThrows(ResponseStatusException.class,
                () -> fixture.service.register(signUp("Jean", "Dupont", "jean@mail.com", "Password123")));

        assertEquals(PasswordPolicy.TOO_COMMON, refused.getReason());
    }

    @Test
    @DisplayName("a refused sign-up never reaches the database")
    void refusedSignUpStoresNothing() {
        Fixture fixture = new Fixture();

        assertThrows(ResponseStatusException.class, () -> fixture.service.register(signUp("Jean", "Dupont", "bad", GOOD_PASSWORD)));
        assertThrows(ResponseStatusException.class, () -> fixture.service.register(signUp("Jean", "Dupont", "jean@mail.com", "short1")));

        assertEquals(0, fixture.users.saved.size());
        assertTrue(fixture.users.existenceChecks.isEmpty(), "the database is not asked about an address that is not valid");
    }

    @Test
    @DisplayName("a correct sign-in returns a token for the account")
    void signInWorks() {
        Fixture fixture = new Fixture();
        User user = fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        AuthenticationResponse response = fixture.service.authenticate(signIn("jean@mail.com", GOOD_PASSWORD));

        assertEquals("jean@mail.com", fixture.jwt.extractUsername(response.getToken()));
        assertTrue(fixture.jwt.isTokenValid(response.getToken(), user));
    }

    @Test
    @DisplayName("a sign-in without e-mail or without password answers 400")
    void signInNeedsBoth() {
        Fixture fixture = new Fixture();

        assertEquals(400, statusOf(() -> fixture.service.authenticate(null)));
        assertEquals(400, statusOf(() -> fixture.service.authenticate(signIn(null, GOOD_PASSWORD))));
        assertEquals(400, statusOf(() -> fixture.service.authenticate(signIn("   ", GOOD_PASSWORD))));
        assertEquals(400, statusOf(() -> fixture.service.authenticate(signIn("jean@mail.com", null))));
        assertEquals(400, statusOf(() -> fixture.service.authenticate(signIn("jean@mail.com", ""))));
        assertEquals(0, fixture.manager.calls);
        assertTrue(fixture.users.lookups.isEmpty());
    }

    @Test
    @DisplayName("an unknown e-mail and a wrong password are both refused as bad credentials")
    void badCredentials() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("nobody@mail.com", GOOD_PASSWORD)));
        assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("jean@mail.com", "wrong-password1")));
    }

    @Test
    @DisplayName("spaces around the e-mail are ignored")
    void signInTrimsTheEmail() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        AuthenticationResponse response = fixture.service.authenticate(signIn("   jean@mail.com  ", GOOD_PASSWORD));

        assertEquals("jean@mail.com", fixture.jwt.extractUsername(response.getToken()));
    }

    @Test
    @DisplayName("an account stored in lower case can sign in with capital letters")
    void signInLowerCaseFallback() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        AuthenticationResponse response = fixture.service.authenticate(signIn("JEAN@Mail.com", GOOD_PASSWORD));

        assertEquals("jean@mail.com", fixture.jwt.extractUsername(response.getToken()));
        assertEquals(List.of("JEAN@Mail.com", "jean@mail.com"), fixture.users.lookups);
    }

    @Test
    @DisplayName("an old account stored with capital letters signs in with the exact address")
    void signInOldAccount() {
        Fixture fixture = new Fixture();

        fixture.addAccount("Old.User@Mail.com", GOOD_PASSWORD);

        AuthenticationResponse response = fixture.service.authenticate(signIn("Old.User@Mail.com", GOOD_PASSWORD));

        assertEquals("Old.User@Mail.com", fixture.jwt.extractUsername(response.getToken()));
        assertEquals(List.of("Old.User@Mail.com"), fixture.users.lookups);
    }

    @Test
    @DisplayName("the sixth attempt answers 429, even with the right password, and checks nothing")
    void lockAfterFiveFailures() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("jean@mail.com", "wrong-password1")));
        }

        int lookups = fixture.users.lookups.size();

        assertEquals(429, statusOf(() -> fixture.service.authenticate(signIn("jean@mail.com", GOOD_PASSWORD))));
        assertEquals(5, fixture.manager.calls);
        assertEquals(lookups, fixture.users.lookups.size());
    }

    @Test
    @DisplayName("four failures do not lock the account")
    void fourFailuresAreFine() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("jean@mail.com", "wrong-password1")));
        }

        assertNotNull(fixture.service.authenticate(signIn("jean@mail.com", GOOD_PASSWORD)).getToken());
    }

    @Test
    @DisplayName("the count is per e-mail address and ignores capital letters and spaces")
    void lockIgnoresCase() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        String[] typed = {"jean@mail.com", "JEAN@mail.com", "Jean@Mail.com", " jean@mail.com ", "JEAN@MAIL.COM"};

        for (String email : typed) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn(email, "wrong-password1")));
        }

        assertEquals(429, statusOf(() -> fixture.service.authenticate(signIn("jean@MAIL.com", GOOD_PASSWORD))));
    }

    @Test
    @DisplayName("another address is not affected by the lock")
    void lockIsPerAddress() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);
        fixture.addAccount("marie@mail.com", GOOD_PASSWORD);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("jean@mail.com", "wrong-password1")));
        }

        assertNotNull(fixture.service.authenticate(signIn("marie@mail.com", GOOD_PASSWORD)).getToken());
    }

    @Test
    @DisplayName("an address that does not exist is locked the same way, so the answers do not tell")
    void unknownAddressIsLockedToo() {
        Fixture fixture = new Fixture();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("ghost@mail.com", GOOD_PASSWORD)));
        }

        int lookups = fixture.users.lookups.size();

        assertEquals(429, statusOf(() -> fixture.service.authenticate(signIn("ghost@mail.com", GOOD_PASSWORD))));
        assertEquals(lookups, fixture.users.lookups.size());
    }

    @Test
    @DisplayName("a correct sign-in clears the count of failures")
    void successClearsTheCount() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("jean@mail.com", "wrong-password1")));
        }

        assertNotNull(fixture.service.authenticate(signIn("jean@mail.com", GOOD_PASSWORD)).getToken());

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(BadCredentialsException.class, () -> fixture.service.authenticate(signIn("jean@mail.com", "wrong-password1")));
        }

        assertNotNull(fixture.service.authenticate(signIn("jean@mail.com", GOOD_PASSWORD)).getToken());
    }

    @Test
    @DisplayName("a request that is not complete is not counted as a failure")
    void incompleteRequestsAreNotCounted() {
        Fixture fixture = new Fixture();

        fixture.addAccount("jean@mail.com", GOOD_PASSWORD);

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertEquals(400, statusOf(() -> fixture.service.authenticate(signIn("jean@mail.com", ""))));
        }

        assertNotNull(fixture.service.authenticate(signIn("jean@mail.com", GOOD_PASSWORD)).getToken());
    }

    @Test
    @DisplayName("a sign-up and then a sign-in with the same account works")
    void signUpThenSignIn() {
        Fixture fixture = new Fixture();

        fixture.service.register(goodSignUp());

        User created = fixture.users.saved.get(0);

        fixture.users.byEmail.put(created.getEmail(), created);
        fixture.manager.passwords.put(created.getEmail(), GOOD_PASSWORD);

        AuthenticationResponse response = fixture.service.authenticate(signIn("Jean.Dupont@mail.com", GOOD_PASSWORD));

        assertEquals("jean.dupont@mail.com", fixture.jwt.extractUsername(response.getToken()));
    }

    @Test
    @DisplayName("a password of 72 characters or less that is longer than 72 bytes answers 400 and is not stored")
    void passwordLongerThanItLooks() {
        Fixture fixture = new Fixture();
        String accented = "\u00e9".repeat(40) + "1";

        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> fixture.service.register(signUp("Jean", "Dupont", "jean@mail.com", accented)));

        assertEquals(400, refused.getStatusCode().value());
        assertEquals(PasswordPolicy.TOO_LONG, refused.getReason());
        assertEquals(0, fixture.users.saved.size());
    }
}
