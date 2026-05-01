export type VatRate = 0 | 6 | 12 | 21;
export type ProductUnit = 'hour' | 'day' | 'piece' | 'forfait';

export interface Product {
  id: string;
  name: string;
  description?: string;
  reference: string;
  unitPrice: number;
  vatRate: number;
  unit: string;
  isActive: boolean;
  createdAt: string;
}

export interface CreateProductRequest {
  name: string;
  description?: string;
  reference: string;
  unitPrice: number;
  vatRate: number;
  unit: string;
}

export type UpdateProductRequest = Partial<CreateProductRequest>;
