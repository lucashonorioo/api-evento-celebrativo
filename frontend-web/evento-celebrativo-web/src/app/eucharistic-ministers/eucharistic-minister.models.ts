export interface EucharisticMinisterResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export interface EucharisticMinisterRequest {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  password: string;
}
