import {
  PersonMinisterialCreateRequest,
  PersonMinisterialUpdateRequest,
} from '../people/person-ministerial-access.models';

export interface EucharisticMinisterResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export type EucharisticMinisterCreateRequest = PersonMinisterialCreateRequest;
export type EucharisticMinisterUpdateRequest = PersonMinisterialUpdateRequest;
