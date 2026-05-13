package com.example.invoiceflow.creditnote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateCreditNoteRequest {

    @NotBlank
    private String reason;

    @NotEmpty
    @Valid
    private List<CreditNoteLineRequest> lines;
}
