package com.example.invoiceflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
        message = "must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, and one digit"
    )
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;
}
