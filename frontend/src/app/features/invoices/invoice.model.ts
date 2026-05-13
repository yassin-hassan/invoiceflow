export type InvoiceStatus =
  | 'DRAFT'
  | 'SENT'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'OVERDUE'
  | 'CANCELLED';

export interface InvoiceLine {
  id: string;
  productId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
  totalExclVat: number;
  totalVat: number;
  totalInclVat: number;
  sortOrder: number;
}

export type CreditNoteStatus = 'DRAFT' | 'ISSUED';

export interface CreditNoteLineSummary {
  invoiceLineId: string;
  quantity: number;
}

export interface CreditNoteSummary {
  id: string;
  number: string | null;
  status: CreditNoteStatus;
  issueDate: string;
  totalInclVat: number;
  lines: CreditNoteLineSummary[];
}

export interface Payment {
  id: string;
  amount: number;
  method: string;
  paidAt: string;
  notes?: string;
  createdAt: string;
}

export interface Invoice {
  id: string;
  number: string | null;
  status: InvoiceStatus;
  clientId: string;
  clientName: string;
  clientEmail: string;
  quoteId?: string;
  issueDate: string;
  dueDate: string;
  paymentTerms?: string;
  createdAt: string;
  sentAt: string | null;
  creditNotes: CreditNoteSummary[];
  creditNoteTotalInclVat: number | null;
  lines: InvoiceLine[];
  payments: Payment[];
  subtotalExclVat: number;
  totalVat: number;
  totalInclVat: number;
  amountPaid: number;
  amountDue: number;
}

export interface InvoiceLineRequest {
  productId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
  sortOrder: number;
}

export interface CreateInvoiceRequest {
  clientId: string;
  issueDate?: string;
  dueDate?: string;
  paymentTerms?: string;
  lines: InvoiceLineRequest[];
}

export interface UpdateInvoiceRequest {
  clientId?: string;
  issueDate?: string;
  dueDate?: string;
  paymentTerms?: string;
  lines: InvoiceLineRequest[];
}

export interface RecordPaymentRequest {
  amount: number;
  method: string;
  paidAt: string;
  notes?: string;
}
