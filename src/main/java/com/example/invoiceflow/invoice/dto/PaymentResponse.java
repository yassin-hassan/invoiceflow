package com.example.invoiceflow.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PaymentResponse {

    private UUID id;
    private BigDecimal amount;
    private String method;
    private LocalDate paidAt;
    private String notes;
    private LocalDateTime createdAt;
}
