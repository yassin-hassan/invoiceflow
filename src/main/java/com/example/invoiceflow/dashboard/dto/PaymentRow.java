package com.example.invoiceflow.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class PaymentRow {
    private UUID id;
    private UUID invoiceId;
    private String invoiceNumber;
    private String clientName;
    private BigDecimal amount;
    private String method;
    private LocalDate paidAt;
}
