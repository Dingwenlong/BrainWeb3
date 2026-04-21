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
public class PasswordResetService {

  private final PasswordResetTicketRepository passwordResetTicketRepository;
  private final AuthProperties authProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public PasswordResetService(
      PasswordResetTicketRepository passwordResetTicketRepository,
      AuthProperties authProperties
  ) {
    this.passwordResetTicketRepository = passwordResetTicketRepository;
    this.authProperties = authProperties;
  }

  @Transactional
  public PasswordResetTicketIssue issueTicket(String userId) {
    Instant now = Instant.now();
    for (PasswordResetTicketEntity entity : passwordResetTicketRepository.findAllByUserIdAndConsumedAtIsNull(userId)) {
      entity.setConsumedAt(now);
    }

    Instant expiresAt = now.plus(authProperties.getPasswordResetTtlMinutes(), ChronoUnit.MINUTES);
    String rawToken = generateOpaqueToken();

    PasswordResetTicketEntity entity = new PasswordResetTicketEntity();
    entity.setId(UUID.randomUUID().toString().replace("-", ""));
    entity.setUserId(userId);
    entity.setTokenHash(hashToken(rawToken));
    entity.setCreatedAt(now);
    entity.setExpiresAt(expiresAt);
    passwordResetTicketRepository.save(entity);
    return new PasswordResetTicketIssue(rawToken, expiresAt);
  }

  @Transactional
  public String consumeTicket(String rawToken) {
    PasswordResetTicketEntity entity = passwordResetTicketRepository.findByTokenHash(hashToken(rawToken))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset ticket is invalid."));
    Instant now = Instant.now();
    if (entity.getConsumedAt() != null || entity.getExpiresAt().isBefore(now)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset ticket is expired.");
    }

    entity.setConsumedAt(now);
    return entity.getUserId();
  }

  private String generateOpaqueToken() {
    byte[] bytes = new byte[32];
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
