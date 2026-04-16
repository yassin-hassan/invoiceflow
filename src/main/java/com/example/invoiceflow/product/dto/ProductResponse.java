package com.example.invoiceflow.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private String reference;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private String unit;
    private boolean isActive;
    private LocalDateTime createdAt;
}
