package com.example.invoiceflow.dashboard.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashboardResponse {
    private DashboardTotals totals;
    private DashboardCounts counts;
    private List<InvoiceSummary> recentInvoices;
    private List<PaymentRow> recentPayments;
    private List<InvoiceSummary> topOverdue;
}
