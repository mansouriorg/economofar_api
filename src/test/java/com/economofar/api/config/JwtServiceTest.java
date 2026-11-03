package com.economofar.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class JwtServiceTest {

    private static final String SECRET = secret(1);
    private static final String OTHER_SECRET = secret(2);

    private static String secret(int seed) {
        byte[] data = new byte[48];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 7 + seed);
        }

        return Base64.getEncoder().encodeToString(data);
    }

    private static UserDetails user(String email) {
        return org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("not-used")
                .authorities("ROLE_USER")
                .build();
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static long lifetimeOf(long hours) {
        JwtService service = new JwtService(SECRET, hours);
        String token = service.generateToken(user("alice@example.com"));

        Date issued = service.extractClaim(token, Claims::getIssuedAt);
        Date expiration = service.extractClaim(token, Claims::getExpiration);

        return expiration.getTime() - issued.getTime();
    }

    private static String secretOf(int length) {
        byte[] data = new byte[length];

        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 11 + 3);
        }

        return Base64.getEncoder().encodeToString(data);
    }

    private static boolean readable(JwtService reader, String token) {
        try {
            return reader.extractUsername(token) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Test
    @DisplayName("a token holds the e-mail and is valid for that user")
    void roundTrip() {
        JwtService service = new JwtService(SECRET, 24);
        UserDetails alice = user("alice@example.com");

        String token = service.generateToken(alice);

        assertEquals(3, token.split("\\.").length);
        assertEquals("alice@example.com", service.extractUsername(token));
        assertTrue(service.isTokenValid(token, alice));
    }

    @Test
    @DisplayName("a token is not valid for another user")
    void otherUser() {
        JwtService service = new JwtService(SECRET, 24);

        String token = service.generateToken(user("alice@example.com"));

        assertFalse(service.isTokenValid(token, user("bob@example.com")));
    }

    @Test
    @DisplayName("the same key accepts the token after a restart")
    void sameKeyAfterRestart() {
        String token = new JwtService(SECRET, 24).generateToken(user("alice@example.com"));

        assertEquals("alice@example.com", new JwtService(SECRET, 24).extractUsername(token));
    }

    @Test
    @DisplayName("another key refuses the token")
    void otherKey() {
        String token = new JwtService(SECRET, 24).generateToken(user("alice@example.com"));
        JwtService other = new JwtService(OTHER_SECRET, 24);

        assertThrows(RuntimeException.class, () -> other.extractUsername(token));
    }

    @Test
    @DisplayName("a token whose content was changed is refused")
    void tamperedToken() {
        JwtService service = new JwtService(SECRET, 24);
        String[] parts = service.generateToken(user("alice@example.com")).split("\\.");
        String changed = parts[0] + "." + "A" + parts[1].substring(1) + "." + parts[2];

        assertThrows(RuntimeException.class, () -> service.extractUsername(changed));
    }

    @Test
    @DisplayName("a token without signature is refused")
    void unsignedToken() {
        JwtService service = new JwtService(SECRET, 24);
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"sub\":\"alice@example.com\",\"exp\":4102444800}");

        assertThrows(RuntimeException.class, () -> service.extractUsername(header + "." + payload + "."));
    }

    @Test
    @DisplayName("text that is not a token is refused")
    void garbage() {
        JwtService service = new JwtService(SECRET, 24);

        assertThrows(RuntimeException.class, () -> service.extractUsername(""));
        assertThrows(RuntimeException.class, () -> service.extractUsername("abc"));
        assertThrows(RuntimeException.class, () -> service.extractUsername("a.b.c"));
    }

    @Test
    @DisplayName("a token that has expired is refused")
    void expiredToken() {
        Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String expired = Jwts.builder()
                .setSubject("alice@example.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000))
                .setExpiration(new Date(System.currentTimeMillis() - 60 * 60 * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        JwtService service = new JwtService(SECRET, 24);

        assertThrows(RuntimeException.class, () -> service.extractUsername(expired));
    }

    @Test
    @DisplayName("a token lasts the number of hours that was asked for")
    void lifetime() {
        long hour = 60L * 60L * 1000L;

        assertEquals(24L * hour, lifetimeOf(24));
        assertEquals(2L * hour, lifetimeOf(2));
        assertEquals(168L * hour, lifetimeOf(168));
    }

    @Test
    @DisplayName("zero or a negative number of hours still gives a token of one hour")
    void lifetimeHasAMinimum() {
        long hour = 60L * 60L * 1000L;

        assertEquals(hour, lifetimeOf(1));
        assertEquals(hour, lifetimeOf(0));
        assertEquals(hour, lifetimeOf(-5));
    }

    @Test
    @DisplayName("a new token is valid for about the time that was asked for")
    void lifetimeFromNow() {
        JwtService service = new JwtService(SECRET, 24);
        String token = service.generateToken(user("alice@example.com"));

        Date expiration = service.extractClaim(token, Claims::getExpiration);
        long left = expiration.getTime() - System.currentTimeMillis();
        long day = 24L * 60L * 60L * 1000L;

        assertTrue(left > day - 60_000L && left <= day);
    }

    @Test
    @DisplayName("extra claims are kept in the token")
    void extraClaims() {
        JwtService service = new JwtService(SECRET, 24);
        Map<String, Object> extra = new HashMap<>();
        extra.put("role", "USER");
        String token = service.generateToken(extra, user("alice@example.com"));

        assertEquals("USER", service.extractClaim(token, claims -> claims.get("role", String.class)));
        assertEquals("alice@example.com", service.extractUsername(token));
    }

    @Test
    @DisplayName("a map that cannot be changed is accepted and the map of the caller is left alone")
    void callerMapIsNotTouched() {
        JwtService service = new JwtService(SECRET, 24);
        Map<String, Object> fixed = Map.of("role", "USER");
        String token = service.generateToken(fixed, user("alice@example.com"));

        assertEquals("USER", service.extractClaim(token, claims -> claims.get("role", String.class)));

        Map<String, Object> mine = new HashMap<>();
        mine.put("role", "ADMIN");
        service.generateToken(mine, user("alice@example.com"));

        assertEquals(1, mine.size());
        assertEquals("ADMIN", mine.get("role"));
    }

    @Test
    @DisplayName("spaces and a line break around the key are ignored")
    void keyIsTrimmed() {
        String token = new JwtService(SECRET, 24).generateToken(user("alice@example.com"));

        assertTrue(readable(new JwtService("  " + SECRET + "\n", 24), token));
    }

    @Test
    @DisplayName("a key of 32 bytes is used, a key of 31 bytes is not")
    void keyLengthLimit() {
        String token32 = new JwtService(secretOf(32), 24).generateToken(user("alice@example.com"));
        String token31 = new JwtService(secretOf(31), 24).generateToken(user("alice@example.com"));

        assertTrue(readable(new JwtService(secretOf(32), 24), token32));
        assertFalse(readable(new JwtService(secretOf(31), 24), token31));
    }

    @Test
    @DisplayName("a missing or unusable key gives a temporary key: the server starts, but old tokens are lost")
    void temporaryKey() {
        String[] unusable = {null, "", "   ", "not base64 at all, with spaces and commas", secretOf(16), secretOf(1)};
        String known = new JwtService(SECRET, 24).generateToken(user("alice@example.com"));

        for (String value : unusable) {
            JwtService service = new JwtService(value, 24);
            String own = service.generateToken(user("alice@example.com"));

            assertEquals("alice@example.com", service.extractUsername(own));
            assertFalse(readable(service, known));
            assertFalse(readable(new JwtService(SECRET, 24), own));
        }
    }

    @Test
    @DisplayName("each temporary key is different")
    void temporaryKeysDiffer() {
        JwtService one = new JwtService("", 24);
        JwtService two = new JwtService("", 24);
        String token = one.generateToken(user("alice@example.com"));

        assertTrue(readable(one, token));
        assertFalse(readable(two, token));
    }
}
