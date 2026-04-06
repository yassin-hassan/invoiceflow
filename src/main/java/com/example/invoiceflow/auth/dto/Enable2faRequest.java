package com.example.invoiceflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class Enable2faRequest {

    @NotBlank
    private String password;

    /**
     * Phone number in E.164 format (e.g. +33612345678).
     * Required by Twilio — the leading + and country code are mandatory.
     */
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be in E.164 format (e.g. +33612345678)")
    private String phone;
}
