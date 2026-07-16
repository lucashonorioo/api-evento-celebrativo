export interface CommentatorResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export interface CommentatorRequest {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  password: string;
}
