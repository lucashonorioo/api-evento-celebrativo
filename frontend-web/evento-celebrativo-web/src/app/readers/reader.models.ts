export interface ReaderResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export interface ReaderRequest {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  password: string;
}
