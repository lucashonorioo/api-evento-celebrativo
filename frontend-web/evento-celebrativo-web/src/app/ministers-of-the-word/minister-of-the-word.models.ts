export interface MinisterOfTheWordResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export interface MinisterOfTheWordRequest {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  password: string;
}
