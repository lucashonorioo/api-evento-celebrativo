import {
  PersonMinisterialCreateRequest,
  PersonMinisterialUpdateRequest,
} from '../people/person-ministerial-access.models';

export interface MinisterOfTheWordResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export type MinisterOfTheWordCreateRequest = PersonMinisterialCreateRequest;
export type MinisterOfTheWordUpdateRequest = PersonMinisterialUpdateRequest;
