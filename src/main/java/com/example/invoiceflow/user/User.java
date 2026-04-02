package com.example.invoiceflow.user;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class User {
    private UUID id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String companyName;
    private String phone;
    private String vatNumber;
    private String logoUrl;
    private String preferredLanguage;
    private boolean isActive;
    private boolean isEmailVerified;
    private boolean is2faEnabled;
    private String twoFaPhone;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
