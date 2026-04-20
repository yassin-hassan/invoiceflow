package com.example.invoiceflow.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateInvoiceRequest {

    private UUID clientId;

    private LocalDate issueDate;

    private LocalDate dueDate;

    private String paymentTerms;

    @Valid
    @NotEmpty
    private List<InvoiceLineRequest> lines;
}
