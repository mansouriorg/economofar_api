package com.economofar.api.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class JwtAuthenticationFilterTest {

    private static final String SECRET = secret(1);

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

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }

        if (type == int.class) {
            return 0;
        }

        if (type == long.class) {
            return 0L;
        }

        return null;
    }

    private static HttpServletRequest request(String authorization) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeader")) {
                        return authorization;
                    }

                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static JwtAuthenticationFilter filter() {
        UserDetailsService users = email -> {
            if (email.equals("alice@example.com")) {
                return user(email);
            }

            throw new UsernameNotFoundException("unknown");
        };

        return new JwtAuthenticationFilter(new JwtService(SECRET, 24), users);
    }

    private static int run(String authorization) throws Exception {
        SecurityContextHolder.clearContext();

        int[] calls = {0};
        FilterChain chain = (request, response) -> calls[0]++;

        filter().doFilterInternal(request(authorization), response(), chain);

        return calls[0];
    }

    private static Authentication result() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.clearContext();
        return authentication;
    }

    @Test
    @DisplayName("a good token signs the user in and the request goes on")
    void goodToken() throws Exception {
        String token = new JwtService(SECRET, 24).generateToken(user("alice@example.com"));

        assertEquals(1, run("Bearer " + token));

        Authentication authentication = result();

        assertNotNull(authentication);
        assertEquals("alice@example.com", ((UserDetails) authentication.getPrincipal()).getUsername());
    }

    @Test
    @DisplayName("no header, another scheme or an empty token: the request goes on, nobody is signed in")
    void noToken() throws Exception {
        assertEquals(1, run(null));
        assertNull(result());

        assertEquals(1, run("Basic abc"));
        assertNull(result());

        assertEquals(1, run("Bearer "));
        assertNull(result());
    }

    @Test
    @DisplayName("a token that is not a token never raises an error")
    void garbageToken() throws Exception {
        assertEquals(1, run("Bearer not-a-token"));
        assertNull(result());

        assertEquals(1, run("Bearer a.b.c"));
        assertNull(result());
    }

    @Test
    @DisplayName("a token signed with another key does not sign anybody in")
    void otherKey() throws Exception {
        String token = new JwtService(secret(2), 24).generateToken(user("alice@example.com"));

        assertEquals(1, run("Bearer " + token));
        assertNull(result());
    }

    @Test
    @DisplayName("a token of an account that no longer exists does not sign anybody in")
    void deletedAccount() throws Exception {
        String token = new JwtService(SECRET, 24).generateToken(user("bob@example.com"));

        assertEquals(1, run("Bearer " + token));
        assertNull(result());
    }

    @Test
    @DisplayName("a token that has expired does not sign anybody in")
    void expiredToken() throws Exception {
        String expired = Jwts.builder()
                .setSubject("alice@example.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000))
                .setExpiration(new Date(System.currentTimeMillis() - 60 * 60 * 1000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();

        assertEquals(1, run("Bearer " + expired));
        assertNull(result());
    }

    @Test
    @DisplayName("a request that is already signed in is left as it is")
    void alreadySignedIn() throws Exception {
        SecurityContextHolder.clearContext();
        Authentication existing = new UsernamePasswordAuthenticationToken("someone", null);
        SecurityContextHolder.getContext().setAuthentication(existing);

        String token = new JwtService(SECRET, 24).generateToken(user("alice@example.com"));
        int[] calls = {0};
        FilterChain chain = (request, response) -> calls[0]++;

        filter().doFilterInternal(request("Bearer " + token), response(), chain);

        assertEquals(1, calls[0]);
        assertEquals(existing, result());
    }

    @Test
    @DisplayName("a token whose content was changed does not sign anybody in")
    void alteredToken() throws Exception {
        String[] parts = new JwtService(SECRET, 24).generateToken(user("alice@example.com")).split("\\.");

        assertEquals(1, run("Bearer " + parts[0] + ".A" + parts[1].substring(1) + "." + parts[2]));
        assertNull(result());
    }
}
