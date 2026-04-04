package com.example.invoiceflow.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountVerificationRepository extends JpaRepository<AccountVerification, UUID> {
    Optional<AccountVerification> findByToken(String token);
    void deleteByUserId(UUID userId);
}
