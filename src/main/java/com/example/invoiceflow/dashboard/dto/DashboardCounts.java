package com.example.invoiceflow.dashboard.dto;

import lombok.Data;

@Data
public class DashboardCounts {
    private long clients;
    private long draftInvoices;
    private long sentInvoices;
    private long overdueInvoices;
    private long paidInvoices;
    private long openQuotes;
}
