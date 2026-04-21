package com.brainweb3.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final AuthProperties authProperties;

  public JwtService(AuthProperties authProperties) {
    this.authProperties = authProperties;
  }

  public JwtSessionToken issueToken(AppUserPrincipal principal) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(authProperties.getTokenTtlHours(), ChronoUnit.HOURS);
    String token = Jwts.builder()
        .subject(principal.actorId())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .claim("role", principal.actorRole())
        .claim("org", principal.actorOrg())
        .claim("name", principal.displayName())
        .signWith(secretKey())
        .compact();
    return new JwtSessionToken(token, expiresAt);
  }

  public String extractSubject(String token) {
    return parseClaims(token).getSubject();
  }

  public boolean isTokenValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException exception) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private SecretKey secretKey() {
    String secret = authProperties.getJwtSecret() == null ? "" : authProperties.getJwtSecret().trim();
    byte[] keyBytes = looksLikeBase64(secret)
        ? Decoders.BASE64.decode(secret)
        : secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("brainweb3.auth.jwt-secret must be at least 32 bytes long.");
    }
    return Keys.hmacShaKeyFor(keyBytes);
  }

  private boolean looksLikeBase64(String value) {
    return value.matches("^[A-Za-z0-9+/=]+$") && value.length() % 4 == 0;
  }
}
