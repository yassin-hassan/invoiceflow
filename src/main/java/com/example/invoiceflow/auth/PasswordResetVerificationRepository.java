package com.example.invoiceflow.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetVerificationRepository extends JpaRepository<PasswordResetVerification, UUID> {
    Optional<PasswordResetVerification> findByToken(String token);
    void deleteByUserId(UUID userId);
}
