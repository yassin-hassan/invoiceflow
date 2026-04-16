package com.example.invoiceflow.quote.dto;

import com.example.invoiceflow.quote.QuoteStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class QuoteResponse {

    private UUID id;
    private String number;
    private QuoteStatus status;
    private UUID clientId;
    private String clientName;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String notes;
    private LocalDateTime createdAt;
    private List<QuoteLineResponse> lines;
    private BigDecimal subtotalExclVat;
    private BigDecimal totalVat;
    private BigDecimal totalInclVat;
}
