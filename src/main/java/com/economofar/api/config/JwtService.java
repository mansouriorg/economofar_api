package com.economofar.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT helper.
 *
 * The signing secret is NOT stored in the source code anymore.
 * It is read from the JWT_SECRET property / environment variable
 * (for example from the .env file of this project).
 *
 * JWT_SECRET must be a Base64 string of at least 32 bytes.
 * Example to generate one:  openssl rand -base64 48
 *
 * JWT_EXPIRATION_HOURS (optional) controls how long a token stays valid.
 * Default: 168 hours (7 days).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final Key signInKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${JWT_SECRET:}") String configuredSecret,
            @Value("${JWT_EXPIRATION_HOURS:168}") long expirationHours
    ) {
        this.expirationMillis = Math.max(1L, expirationHours) * 60L * 60L * 1000L;
        this.signInKey = buildKey(configuredSecret);
    }

    private Key buildKey(String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            try {
                byte[] keyBytes = Decoders.BASE64.decode(configuredSecret.trim());

                if (keyBytes.length >= 32) {
                    return Keys.hmacShaKeyFor(keyBytes);
                }

                log.warn("JWT_SECRET is too short (minimum 32 bytes once decoded). A temporary key is used instead.");
            } catch (RuntimeException exception) {
                log.warn("JWT_SECRET is not valid Base64. A temporary key is used instead.");
            }
        } else {
            log.warn("JWT_SECRET is not configured. A temporary key is used: every login is lost when the server restarts.");
        }

        byte[] randomKey = new byte[64];
        new SecureRandom().nextBytes(randomKey);
        return Keys.hmacShaKeyFor(randomKey);
    }

    /**
     * Extracts the username (the email) from the token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generates a token for a user (without extra claims).
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a token with extra claims.
     */
    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setClaims(new HashMap<>(extraClaims))
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMillis))
                .signWith(signInKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Checks that the token belongs to this user and is not expired.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username != null
                && username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(signInKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
