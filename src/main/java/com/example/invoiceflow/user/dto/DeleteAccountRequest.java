package com.example.invoiceflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    @NotBlank
    private String currentPassword;
}
