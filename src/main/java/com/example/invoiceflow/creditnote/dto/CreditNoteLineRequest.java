package com.example.invoiceflow.creditnote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreditNoteLineRequest {

    @NotNull
    private UUID invoiceLineId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    private Integer sortOrder;
}
