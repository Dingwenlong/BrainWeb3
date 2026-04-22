package com.brainweb3.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveTextSanitizerTests {

  private final SensitiveTextSanitizer sensitiveTextSanitizer = new SensitiveTextSanitizer();

  @Test
  void redactsCredentialLikeTokensAcrossCommonFormats() {
    String sanitized = sensitiveTextSanitizer.sanitize(
        """
            password=brainweb3-demo \
            {"resetToken":"reset-raw-123"} \
            Authorization=Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature \
            https://storage.example/upload?token=url-secret-456
            """
    );

    assertTrue(sanitized.contains("password=[REDACTED]"));
    assertTrue(sanitized.contains("\"resetToken\":\"[REDACTED]\""));
    assertTrue(sanitized.contains("Authorization=[REDACTED]"));
    assertTrue(sanitized.contains("?token=[REDACTED]"));
    assertFalse(sanitized.contains("brainweb3-demo"));
    assertFalse(sanitized.contains("reset-raw-123"));
    assertFalse(sanitized.contains("url-secret-456"));
    assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"));
  }
}
