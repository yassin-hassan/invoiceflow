package com.example.invoiceflow.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateInvoiceRequest {

    @NotNull
    private UUID clientId;

    private LocalDate issueDate;

    private LocalDate dueDate;

    private String paymentTerms;

    @NotEmpty
    @Valid
    private List<InvoiceLineRequest> lines;
}
