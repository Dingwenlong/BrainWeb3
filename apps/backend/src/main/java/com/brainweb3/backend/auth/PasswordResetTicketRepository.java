package com.brainweb3.backend.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTicketRepository extends JpaRepository<PasswordResetTicketEntity, String> {
  Optional<PasswordResetTicketEntity> findByTokenHash(String tokenHash);
  List<PasswordResetTicketEntity> findAllByUserIdAndConsumedAtIsNull(String userId);
}
