package com.example.invoiceflow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TwoFactorVerificationRepository extends JpaRepository<TwoFactorVerification, UUID> {
    Optional<TwoFactorVerification> findByUserIdAndCode(UUID userId, String code);

    @Modifying
    @Query("DELETE FROM TwoFactorVerification t WHERE t.user.id = :userId")
    void deleteByUserId(UUID userId);
}
