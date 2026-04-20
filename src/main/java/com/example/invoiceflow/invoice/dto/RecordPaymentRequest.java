package com.example.invoiceflow.invoice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecordPaymentRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 50)
    private String method;

    @NotNull
    private LocalDate paidAt;

    private String notes;
}
