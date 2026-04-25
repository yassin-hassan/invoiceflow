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

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  reference?: string;
  unitPrice?: number;
  vatRate?: number;
  unit?: string;
}
