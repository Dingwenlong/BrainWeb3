package com.brainweb3.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brainweb3.identity")
public class IdentityProperties {

  private String issuerDid = "did:brainweb3:issuer:platform";
  private long credentialTtlDays = 365;
  private String credentialSecret = "brainweb3-demo-identity-secret-2026-04-21";
  private List<String> revokedActors = List.of();
  private List<String> revokedOrganizations = List.of();

  public String getIssuerDid() {
    return issuerDid;
  }

  public void setIssuerDid(String issuerDid) {
    this.issuerDid = issuerDid;
  }

  public long getCredentialTtlDays() {
    return credentialTtlDays;
  }

  public void setCredentialTtlDays(long credentialTtlDays) {
    this.credentialTtlDays = credentialTtlDays;
  }

  public String getCredentialSecret() {
    return credentialSecret;
  }

  public void setCredentialSecret(String credentialSecret) {
    this.credentialSecret = credentialSecret;
  }

  public List<String> getRevokedActors() {
    return revokedActors;
  }

  public void setRevokedActors(List<String> revokedActors) {
    this.revokedActors = revokedActors;
  }

  public List<String> getRevokedOrganizations() {
    return revokedOrganizations;
  }

  public void setRevokedOrganizations(List<String> revokedOrganizations) {
    this.revokedOrganizations = revokedOrganizations;
  }
}
