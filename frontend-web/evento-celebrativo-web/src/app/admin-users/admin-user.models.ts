export type PersonType =
  | 'reader'
  | 'commentator'
  | 'minister_of_the_word'
  | 'eucharistic_minister'
  | 'priest';

export type UserRole = 'ROLE_ADMIN' | 'ROLE_OPERATOR';

export interface PersonAdmin {
  readonly id: number;
  readonly name: string;
  readonly phoneNumber: string;
  readonly personType: PersonType;
  readonly roles: UserRole[];
}

export interface PersonAdminFilters {
  readonly name?: string;
  readonly phoneNumber?: string;
  readonly personType?: PersonType;
  readonly role?: UserRole;
  readonly page: number;
  readonly size: number;
}

export interface PersonRoleUpdateRequest {
  readonly role: UserRole;
}

export interface PersonAdminPage {
  readonly content: PersonAdmin[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly number: number;
  readonly size: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly empty: boolean;
}
