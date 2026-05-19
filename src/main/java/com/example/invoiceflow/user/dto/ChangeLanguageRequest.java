package com.example.invoiceflow.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangeLanguageRequest {

    @NotNull
    @Pattern(regexp = "^(FR|EN)$", message = "{validation.language.invalid}")
    private String language;
}
