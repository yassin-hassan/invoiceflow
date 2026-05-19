package com.example.invoiceflow.security;

import com.example.invoiceflow.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HexFormat;

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, Role role) {
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public Role extractRole(String token) {
        String raw = parse(token).get(CLAIM_ROLE, String.class);
        if (raw == null) return Role.USER;
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Role.USER;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
