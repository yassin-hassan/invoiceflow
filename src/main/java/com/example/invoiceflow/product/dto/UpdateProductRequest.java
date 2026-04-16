package com.example.invoiceflow.product.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {

    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 100)
    private String reference;

    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 8, fraction = 2)
    private BigDecimal unitPrice;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal vatRate;

    @Size(max = 50)
    private String unit;
}
