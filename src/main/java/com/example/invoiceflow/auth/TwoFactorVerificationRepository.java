package com.example.invoiceflow.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TwoFactorVerificationRepository extends JpaRepository<TwoFactorVerification, UUID> {
    Optional<TwoFactorVerification> findByUserIdAndCode(UUID userId, String code);
    void deleteByUserId(UUID userId);
}
