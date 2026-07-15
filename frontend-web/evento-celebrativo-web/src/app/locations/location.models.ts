export interface LocationResponse {
  id: number;
  churchName: string;
  address: string;
}

export interface LocationRequest {
  churchName: string;
  address: string;
}
