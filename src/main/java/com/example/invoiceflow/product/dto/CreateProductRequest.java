package com.example.invoiceflow.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @NotBlank
    @Size(max = 100)
    private String reference;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 8, fraction = 2)
    private BigDecimal unitPrice;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal vatRate;

    @NotBlank
    @Size(max = 50)
    private String unit;
}
