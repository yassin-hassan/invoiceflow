package com.example.invoiceflow.dashboard.dto;

import com.example.invoiceflow.invoice.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class InvoiceSummary {
    private UUID id;
    private String number;
    private String clientName;
    private InvoiceStatus status;
    private BigDecimal totalInclVat;
    private BigDecimal amountDue;
    private LocalDate issueDate;
    private LocalDate dueDate;
}
