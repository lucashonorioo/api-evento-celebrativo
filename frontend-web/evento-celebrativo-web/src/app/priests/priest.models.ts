import {
  PersonMinisterialCreateRequest,
  PersonMinisterialUpdateRequest,
} from '../people/person-ministerial-access.models';

export interface PriestResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export type PriestCreateRequest = PersonMinisterialCreateRequest;
export type PriestUpdateRequest = PersonMinisterialUpdateRequest;
