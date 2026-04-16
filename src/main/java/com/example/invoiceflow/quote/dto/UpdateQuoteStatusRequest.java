package com.example.invoiceflow.quote.dto;

import com.example.invoiceflow.quote.QuoteStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateQuoteStatusRequest {

    @NotNull
    private QuoteStatus status;
}
