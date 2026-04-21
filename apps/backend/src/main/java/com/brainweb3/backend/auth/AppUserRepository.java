package com.brainweb3.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUserEntity, String> {
  List<AppUserEntity> findAllByOrderByCreatedAtDesc();
  long countByRoleCodeIgnoreCaseAndStatusIgnoreCaseAndIdNot(String roleCode, String status, String id);
  boolean existsByOrganizationIgnoreCaseAndStatusIgnoreCase(String organization, String status);
}
