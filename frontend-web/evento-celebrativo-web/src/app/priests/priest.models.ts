export interface PriestResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export interface PriestRequest {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  password: string;
}
