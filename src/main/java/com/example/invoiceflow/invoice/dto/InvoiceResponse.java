package com.example.invoiceflow.invoice.dto;

import com.example.invoiceflow.invoice.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class InvoiceResponse {

    private UUID id;
    private String number;
    private InvoiceStatus status;
    private UUID clientId;
    private String clientName;
    private UUID quoteId;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String paymentTerms;
    private LocalDateTime createdAt;
    private List<InvoiceLineResponse> lines;
    private List<PaymentResponse> payments;
    private BigDecimal subtotalExclVat;
    private BigDecimal totalVat;
    private BigDecimal totalInclVat;
    private BigDecimal amountPaid;
    private BigDecimal amountDue;
}
