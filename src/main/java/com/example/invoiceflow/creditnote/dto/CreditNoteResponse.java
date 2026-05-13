package com.example.invoiceflow.creditnote.dto;

import com.example.invoiceflow.creditnote.CreditNoteStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreditNoteResponse {

    private UUID id;
    private String number;
    private CreditNoteStatus status;
    private UUID originalInvoiceId;
    private String originalInvoiceNumber;
    private UUID clientId;
    private String clientName;
    private LocalDate issueDate;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime issuedAt;
    private List<CreditNoteLineResponse> lines;
    private BigDecimal subtotalExclVat;
    private BigDecimal totalVat;
    private BigDecimal totalInclVat;
}
