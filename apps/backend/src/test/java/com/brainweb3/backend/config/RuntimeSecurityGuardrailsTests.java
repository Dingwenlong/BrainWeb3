package com.brainweb3.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.brainweb3.backend.auth.AuthProperties;
import org.junit.jupiter.api.Test;

class RuntimeSecurityGuardrailsTests {

  @Test
  void allowsBootstrapLikeStagesToKeepDemoDefaults() {
    RuntimeSecurityGuardrails guardrails = new RuntimeSecurityGuardrails(
        "local",
        defaultAuthProperties(),
        defaultIdentityProperties()
    );

    assertDoesNotThrow(guardrails::validate);
  }

  @Test
  void blocksProductionLikeStageWhenDefaultsRemainEnabled() {
    RuntimeSecurityGuardrails guardrails = new RuntimeSecurityGuardrails(
        "production",
        defaultAuthProperties(),
        defaultIdentityProperties()
    );

    assertThrows(IllegalStateException.class, guardrails::validate);
  }

  @Test
  void allowsProductionLikeStageAfterSecretsAndDemoFlagsAreHardened() {
    AuthProperties authProperties = defaultAuthProperties();
    authProperties.setJwtSecret("replace-with-a-production-jwt-secret-2026-04-22");
    authProperties.setDemoPassword("replace-with-a-production-demo-password");
    authProperties.setAllowDemoBootstrap(false);
    authProperties.setAllowDemoPasswordLogin(false);

    IdentityProperties identityProperties = defaultIdentityProperties();
    identityProperties.setCredentialSecret("replace-with-a-production-identity-secret-2026-04-22");

    RuntimeSecurityGuardrails guardrails = new RuntimeSecurityGuardrails(
        "production",
        authProperties,
        identityProperties
    );

    assertDoesNotThrow(guardrails::validate);
  }

  private AuthProperties defaultAuthProperties() {
    AuthProperties authProperties = new AuthProperties();
    authProperties.setJwtSecret(RuntimeSecurityGuardrails.DEFAULT_JWT_SECRET);
    authProperties.setDemoPassword(RuntimeSecurityGuardrails.DEFAULT_DEMO_PASSWORD);
    authProperties.setAllowDemoBootstrap(true);
    authProperties.setAllowDemoPasswordLogin(true);
    return authProperties;
  }

  private IdentityProperties defaultIdentityProperties() {
    IdentityProperties identityProperties = new IdentityProperties();
    identityProperties.setCredentialSecret(RuntimeSecurityGuardrails.DEFAULT_IDENTITY_SECRET);
    return identityProperties;
  }
}
