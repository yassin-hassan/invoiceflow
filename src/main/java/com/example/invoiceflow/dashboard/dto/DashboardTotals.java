package com.example.invoiceflow.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardTotals {
    private BigDecimal revenue;
    private BigDecimal outstanding;
    private BigDecimal overdue;
}
