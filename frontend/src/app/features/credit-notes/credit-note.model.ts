export type CreditNoteStatus = 'DRAFT' | 'ISSUED';

export interface CreditNoteLine {
  id: string;
  invoiceLineId: string;
  description: string;
  unitPrice: number;
  vatRate: number;
  quantity: number;
  sortOrder: number;
  totalExclVat: number;
  totalVat: number;
  totalInclVat: number;
}

export interface CreditNote {
  id: string;
  number: string | null;
  status: CreditNoteStatus;
  originalInvoiceId: string;
  originalInvoiceNumber: string | null;
  clientId: string;
  clientName: string;
  issueDate: string;
  reason: string;
  createdAt: string;
  issuedAt: string | null;
  lines: CreditNoteLine[];
  subtotalExclVat: number;
  totalVat: number;
  totalInclVat: number;
}

export interface CreditNoteLineRequest {
  invoiceLineId: string;
  quantity: number;
  sortOrder?: number;
}

export interface CreateCreditNoteRequest {
  reason: string;
  issueDate?: string;
  lines: CreditNoteLineRequest[];
}

export interface UpdateCreditNoteRequest {
  reason: string;
  lines: CreditNoteLineRequest[];
}
