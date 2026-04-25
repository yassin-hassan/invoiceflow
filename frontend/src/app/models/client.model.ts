export interface Address {
  street?: string;
  postalCode?: string;
  city?: string;
  country?: string;
}

export interface Client {
  id: string;
  name: string;
  email: string;
  phone?: string;
  vatNumber?: string;
  notes?: string;
  isActive: boolean;
  createdAt: string;
  billingAddress?: Address;
}

export interface CreateClientRequest {
  name: string;
  email: string;
  phone?: string;
  vatNumber?: string;
  notes?: string;
  billingAddress?: Address;
}

export interface UpdateClientRequest {
  name?: string;
  email?: string;
  phone?: string;
  vatNumber?: string;
  notes?: string;
  billingAddress?: Address;
}
