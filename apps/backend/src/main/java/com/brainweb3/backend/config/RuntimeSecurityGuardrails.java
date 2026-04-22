package com.brainweb3.backend.config;

import com.brainweb3.backend.auth.AuthProperties;
import java.util.Locale;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeSecurityGuardrails implements SmartInitializingSingleton {

  static final String DEFAULT_JWT_SECRET = "brainweb3-demo-jwt-secret-2026-04-21-neural-trust-console";
  static final String DEFAULT_IDENTITY_SECRET = "brainweb3-demo-identity-secret-2026-04-21";
  static final String DEFAULT_DEMO_PASSWORD = "brainweb3-demo";

  private final String stage;
  private final AuthProperties authProperties;
  private final IdentityProperties identityProperties;

  public RuntimeSecurityGuardrails(
      @Value("${brainweb3.stage:bootstrap}") String stage,
      AuthProperties authProperties,
      IdentityProperties identityProperties
  ) {
    this.stage = stage;
    this.authProperties = authProperties;
    this.identityProperties = identityProperties;
  }

  @Override
  public void afterSingletonsInstantiated() {
    validate();
  }

  void validate() {
    if (!requiresProductionGuardrails(stage)) {
      return;
    }

    if (DEFAULT_JWT_SECRET.equals(trim(authProperties.getJwtSecret()))) {
      throw new IllegalStateException("Production-like stage requires replacing the default JWT secret.");
    }
    if (DEFAULT_IDENTITY_SECRET.equals(trim(identityProperties.getCredentialSecret()))) {
      throw new IllegalStateException("Production-like stage requires replacing the default identity credential secret.");
    }
    if (DEFAULT_DEMO_PASSWORD.equals(trim(authProperties.getDemoPassword()))) {
      throw new IllegalStateException("Production-like stage requires replacing the default demo password.");
    }
    if (authProperties.isAllowDemoBootstrap()) {
      throw new IllegalStateException("Production-like stage must disable demo account bootstrap.");
    }
    if (authProperties.isAllowDemoPasswordLogin()) {
      throw new IllegalStateException("Production-like stage must disable demo password login.");
    }
  }

  public static boolean requiresProductionGuardrails(String stage) {
    String normalized = trim(stage).toLowerCase(Locale.ROOT);
    return !normalized.isBlank()
        && !normalized.equals("bootstrap")
        && !normalized.equals("local")
        && !normalized.equals("dev")
        && !normalized.equals("test")
        && !normalized.equals("ci");
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }
}
