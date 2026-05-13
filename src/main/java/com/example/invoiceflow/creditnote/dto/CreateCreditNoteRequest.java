package com.example.invoiceflow.creditnote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateCreditNoteRequest {

    @NotBlank
    private String reason;

    private LocalDate issueDate;

    @NotEmpty
    @Valid
    private List<CreditNoteLineRequest> lines;
}
