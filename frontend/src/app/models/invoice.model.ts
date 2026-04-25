export type InvoiceStatus = 'DRAFT' | 'SENT' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

export interface InvoiceLineRequest {
  productId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
  sortOrder?: number;
}

export interface InvoiceLineResponse {
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
  number: string;
  status: InvoiceStatus;
  clientId: string;
  clientName: string;
  quoteId?: string;
  issueDate: string;
  dueDate: string;
  paymentTerms?: string;
  createdAt: string;
  lines: InvoiceLineResponse[];
  payments: Payment[];
  subtotalExclVat: number;
  totalVat: number;
  totalInclVat: number;
  amountPaid: number;
  amountDue: number;
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
