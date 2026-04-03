package com.example.invoiceflow.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HexFormat;

// Pure cryptographic utility — knows nothing about HTTP or Spring Security.
// Responsible for creating JWTs on login and validating them on subsequent requests.
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    // @Value injects values from application.yaml at startup.
    // The hex secret is decoded into a SecretKey — a format jjwt can use for signing.
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret));
        this.expirationMs = expirationMs;
    }

    // Builds a signed JWT. The "subject" claim holds the user's email — this is how
    // we identify who the token belongs to when it comes back on future requests.
    // The token is signed with the secret key so nobody can forge or tamper with it.
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // Parses the token, verifies the signature, and returns the email stored in the subject claim.
    // Will throw if the token is invalid or expired — call isTokenValid() first.
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // Validates the token by attempting to parse it. jjwt throws different exceptions
    // for expired tokens, tampered signatures, malformed tokens, etc. — we treat all
    // failures the same: the token is not valid.
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
