package com.brainweb3.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "brainweb3.auth")
public class AuthProperties {

  private String jwtSecret = "";
  private long tokenTtlHours = 12;
  private long refreshTokenTtlDays = 14;
  private long passwordResetTtlMinutes = 30;
  private String demoPassword = "brainweb3-demo";
  private boolean allowDemoBootstrap = true;
  private boolean allowDemoPasswordLogin = true;

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public long getTokenTtlHours() {
    return tokenTtlHours;
  }

  public void setTokenTtlHours(long tokenTtlHours) {
    this.tokenTtlHours = tokenTtlHours;
  }

  public long getRefreshTokenTtlDays() {
    return refreshTokenTtlDays;
  }

  public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
    this.refreshTokenTtlDays = refreshTokenTtlDays;
  }

  public long getPasswordResetTtlMinutes() {
    return passwordResetTtlMinutes;
  }

  public void setPasswordResetTtlMinutes(long passwordResetTtlMinutes) {
    this.passwordResetTtlMinutes = passwordResetTtlMinutes;
  }

  public String getDemoPassword() {
    return demoPassword;
  }

  public void setDemoPassword(String demoPassword) {
    this.demoPassword = demoPassword;
  }

  public boolean isAllowDemoBootstrap() {
    return allowDemoBootstrap;
  }

  public void setAllowDemoBootstrap(boolean allowDemoBootstrap) {
    this.allowDemoBootstrap = allowDemoBootstrap;
  }

  public boolean isAllowDemoPasswordLogin() {
    return allowDemoPasswordLogin;
  }

  public void setAllowDemoPasswordLogin(boolean allowDemoPasswordLogin) {
    this.allowDemoPasswordLogin = allowDemoPasswordLogin;
  }
}
