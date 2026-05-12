import { InvoiceStatus } from '../invoices/invoice.model';

export interface DashboardTotals {
  revenue: number;
  outstanding: number;
  overdue: number;
}

export interface DashboardCounts {
  clients: number;
  draftInvoices: number;
  sentInvoices: number;
  overdueInvoices: number;
  paidInvoices: number;
  openQuotes: number;
}

export interface InvoiceSummary {
  id: string;
  number: string;
  clientName: string;
  status: InvoiceStatus;
  totalInclVat: number;
  amountDue: number;
  issueDate: string;
  dueDate: string;
}

export interface PaymentRow {
  id: string;
  invoiceId: string;
  invoiceNumber: string;
  clientName: string;
  amount: number;
  method: string;
  paidAt: string;
}

export interface DashboardResponse {
  totals: DashboardTotals;
  counts: DashboardCounts;
  recentInvoices: InvoiceSummary[];
  recentPayments: PaymentRow[];
  topOverdue: InvoiceSummary[];
}
