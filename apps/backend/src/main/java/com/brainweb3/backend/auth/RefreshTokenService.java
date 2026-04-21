package com.brainweb3.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthProperties authProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AuthProperties authProperties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.authProperties = authProperties;
  }

  @Transactional
  public RefreshTokenIssue issueToken(String userId) {
    return persistToken(userId);
  }

  @Transactional
  public RefreshTokenRotation rotate(String rawToken) {
    RefreshTokenEntity entity = requireActiveToken(rawToken);
    Instant now = Instant.now();
    entity.setLastUsedAt(now);
    entity.setRevokedAt(now);

    RefreshTokenIssue replacement = persistToken(entity.getUserId());
    entity.setReplacedByTokenId(replacement.tokenId());
    return new RefreshTokenRotation(entity.getUserId(), replacement.refreshToken(), replacement.expiresAt());
  }

  @Transactional
  public void revokeAllActiveTokens(String userId) {
    Instant now = Instant.now();
    for (RefreshTokenEntity entity : refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)) {
      entity.setRevokedAt(now);
    }
  }

  private RefreshTokenIssue persistToken(String userId) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(authProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS);
    String rawToken = generateOpaqueToken();

    RefreshTokenEntity entity = new RefreshTokenEntity();
    entity.setId(UUID.randomUUID().toString().replace("-", ""));
    entity.setUserId(userId);
    entity.setTokenHash(hashToken(rawToken));
    entity.setCreatedAt(now);
    entity.setExpiresAt(expiresAt);
    refreshTokenRepository.save(entity);
    return new RefreshTokenIssue(entity.getId(), rawToken, expiresAt);
  }

  private RefreshTokenEntity requireActiveToken(String rawToken) {
    RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(hashToken(rawToken))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid."));
    Instant now = Instant.now();
    if (entity.getRevokedAt() != null || entity.getExpiresAt().isBefore(now)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired.");
    }
    return entity;
  }

  private String generateOpaqueToken() {
    byte[] bytes = new byte[48];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }
}
