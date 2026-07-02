package com.solux31.nubee_BE.global.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the JJWT dependencies added in build.gradle
 * (jjwt-api, jjwt-impl, jjwt-jackson) are correctly wired together
 * and can issue and parse signed JWTs.
 */
class JwtDependencyTest {

    private final SecretKey key = Jwts.SIG.HS256.key().build();

    @Test
    void issuesAndParsesSignedJwtWithClaims() {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + 60_000);

        String token = Jwts.builder()
                .subject("test-user")
                .claim("role", "USER")
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "Compact JWT should have header, payload and signature segments");

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("test-user", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
        assertEquals(expiration.getTime() / 1000, claims.getExpiration().getTime() / 1000);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = Jwts.builder()
                .subject("test-user")
                .signWith(key)
                .compact();

        SecretKey otherKey = Jwts.SIG.HS256.key().build();

        assertThrows(SignatureException.class, () ->
                Jwts.parser()
                        .verifyWith(otherKey)
                        .build()
                        .parseSignedClaims(token));
    }

    @Test
    void rejectsExpiredToken() {
        Date past = new Date(System.currentTimeMillis() - 10_000);

        String token = Jwts.builder()
                .subject("test-user")
                .expiration(past)
                .signWith(key)
                .compact();

        assertThrows(ExpiredJwtException.class, () ->
                Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token));
    }

    @Test
    void rejectsTamperedTokenPayload() {
        String token = Jwts.builder()
                .subject("test-user")
                .signWith(key)
                .compact();

        String[] parts = token.split("\\.");
        // Flip the last character of the signature segment to simulate tampering,
        // guaranteeing the character actually changes regardless of its original value.
        String signature = parts[2];
        char lastChar = signature.charAt(signature.length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tamperedSignature = signature.substring(0, signature.length() - 1) + replacement;
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThrows(SignatureException.class, () ->
                Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(tamperedToken));
    }
}