package com.example.invoiceflow.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InvoiceLineResponse {

    private UUID id;
    private UUID productId;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal vatRate;
    private BigDecimal totalExclVat;
    private BigDecimal totalVat;
    private BigDecimal totalInclVat;
    private int sortOrder;
}
