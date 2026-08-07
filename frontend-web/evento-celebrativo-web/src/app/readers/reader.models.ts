import {
  PersonMinisterialCreateRequest,
  PersonMinisterialUpdateRequest,
} from '../people/person-ministerial-access.models';

export interface ReaderResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export type ReaderCreateRequest = PersonMinisterialCreateRequest;
export type ReaderUpdateRequest = PersonMinisterialUpdateRequest;
