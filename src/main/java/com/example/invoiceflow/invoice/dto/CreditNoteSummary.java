package com.example.invoiceflow.invoice.dto;

import com.example.invoiceflow.creditnote.CreditNoteStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreditNoteSummary {

    private UUID id;
    private String number;
    private CreditNoteStatus status;
    private LocalDate issueDate;
    private BigDecimal totalInclVat;
    private List<CreditNoteLineSummary> lines;

    @Data
    public static class CreditNoteLineSummary {
        private UUID invoiceLineId;
        private BigDecimal quantity;
    }
}
