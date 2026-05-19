package com.example.invoiceflow.admin.dto;

import com.example.invoiceflow.user.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    @NotNull
    private Role role;
}
