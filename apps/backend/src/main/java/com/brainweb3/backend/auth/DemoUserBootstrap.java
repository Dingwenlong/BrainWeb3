package com.brainweb3.backend.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoUserBootstrap implements ApplicationRunner {

  private static final List<SeedUser> DEMO_USERS = List.of(
      new SeedUser("researcher-01", "研究员一号", "researcher", "Sichuan Neuro Lab"),
      new SeedUser("owner-01", "归属方一号", "owner", "Huaxi Medical Union"),
      new SeedUser("approver-01", "审批人一号", "approver", "Huaxi Medical Union"),
      new SeedUser("admin-01", "平台管理员", "admin", "Huaxi Medical Union")
  );

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthProperties authProperties;

  public DemoUserBootstrap(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      AuthProperties authProperties
  ) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.authProperties = authProperties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!authProperties.isAllowDemoBootstrap()) {
      return;
    }

    Instant now = Instant.now();
    for (SeedUser seedUser : DEMO_USERS) {
      if (appUserRepository.existsById(seedUser.actorId())) {
        continue;
      }

      AppUserEntity entity = new AppUserEntity();
      entity.setId(seedUser.actorId());
      entity.setDisplayName(seedUser.displayName());
      entity.setRoleCode(seedUser.actorRole());
      entity.setOrganization(seedUser.organization());
      entity.setStatus("active");
      entity.setPasswordHash(passwordEncoder.encode(authProperties.getDemoPassword()));
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      entity.setPasswordChangedAt(now);
      appUserRepository.save(entity);
    }
  }

  private record SeedUser(
      String actorId,
      String displayName,
      String actorRole,
      String organization
  ) {
  }
}
