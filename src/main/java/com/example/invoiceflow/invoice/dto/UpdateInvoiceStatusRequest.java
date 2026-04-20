package com.example.invoiceflow.invoice.dto;

import com.example.invoiceflow.invoice.InvoiceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInvoiceStatusRequest {

    @NotNull
    private InvoiceStatus status;
}
