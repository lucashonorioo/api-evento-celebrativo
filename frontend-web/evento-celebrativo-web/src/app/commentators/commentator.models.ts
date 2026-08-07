import {
  PersonMinisterialCreateRequest,
  PersonMinisterialUpdateRequest,
} from '../people/person-ministerial-access.models';

export interface CommentatorResponse {
  id: number;
  name: string;
  phoneNumber: string | null;
  birthdayDate: string | null;
}

export type CommentatorCreateRequest = PersonMinisterialCreateRequest;
export type CommentatorUpdateRequest = PersonMinisterialUpdateRequest;
