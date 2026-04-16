package com.example.invoiceflow.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateQuoteRequest {

    private UUID clientId;

    private LocalDate issueDate;

    private LocalDate expiryDate;

    private String notes;

    @Valid
    @NotEmpty
    private List<QuoteLineRequest> lines;
}
