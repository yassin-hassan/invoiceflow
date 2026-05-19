package com.example.invoiceflow.admin.dto;

import com.example.invoiceflow.user.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class AdminUserListItem {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String companyName;
    private Role role;
    private boolean active;
    private boolean emailVerified;
    private boolean twoFaEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private long clientCount;
    private long invoiceCount;
    private BigDecimal totalRevenue;
}
