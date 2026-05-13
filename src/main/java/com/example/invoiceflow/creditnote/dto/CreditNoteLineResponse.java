package com.example.invoiceflow.creditnote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreditNoteLineResponse {

    private UUID id;
    private UUID invoiceLineId;
    private String description;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private BigDecimal quantity;
    private BigDecimal totalExclVat;
    private BigDecimal totalVat;
    private BigDecimal totalInclVat;
    private Integer sortOrder;
}
