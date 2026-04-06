package com.example.invoiceflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Disable2faRequest {

    @NotBlank
    private String password;
}
