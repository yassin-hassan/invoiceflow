package com.example.invoiceflow.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateQuoteRequest {

    @NotNull
    private UUID clientId;

    private LocalDate issueDate;

    private LocalDate expiryDate;

    private String notes;

    @NotEmpty
    @Valid
    private List<QuoteLineRequest> lines;
}
