export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  companyName?: string;
  vatNumber?: string;
  logoUrl?: string;
  preferredLanguage: string;
  twoFaEnabled: boolean;
}
