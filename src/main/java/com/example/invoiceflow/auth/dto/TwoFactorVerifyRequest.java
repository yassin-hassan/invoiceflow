package com.example.invoiceflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorVerifyRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code;
}
