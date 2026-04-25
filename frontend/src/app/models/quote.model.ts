export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'CONVERTED';

export interface QuoteLineRequest {
  productId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
  sortOrder?: number;
}

export interface QuoteLineResponse {
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

export interface Quote {
  id: string;
  number: string;
  status: QuoteStatus;
  clientId: string;
  clientName: string;
  issueDate: string;
  expiryDate: string;
  notes?: string;
  createdAt: string;
  lines: QuoteLineResponse[];
  subtotalExclVat: number;
  totalVat: number;
  totalInclVat: number;
}

export interface CreateQuoteRequest {
  clientId: string;
  issueDate?: string;
  expiryDate?: string;
  notes?: string;
  lines: QuoteLineRequest[];
}

export interface UpdateQuoteRequest {
  clientId?: string;
  issueDate?: string;
  expiryDate?: string;
  notes?: string;
  lines: QuoteLineRequest[];
}
