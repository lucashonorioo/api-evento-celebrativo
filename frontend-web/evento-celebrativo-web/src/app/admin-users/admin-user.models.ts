export type MinistryType =
  | 'PRIEST'
  | 'READER'
  | 'COMMENTATOR'
  | 'MINISTER_OF_THE_WORD'
  | 'EUCHARISTIC_MINISTER';

export type UserRole = 'ROLE_ADMIN' | 'ROLE_OPERATOR';

export interface PersonAdmin {
  readonly id: number;
  readonly name: string;
  readonly phoneNumber: string;
  readonly birthdayDate: string;
  readonly personActive: boolean;
  readonly ministries: MinistryType[];
  readonly accountExists: boolean;
  readonly accountEnabled: boolean | null;
  readonly username: string | null;
  readonly roles: UserRole[];
}

export interface PersonAdminFilters {
  readonly name?: string;
  readonly phoneNumber?: string;
  readonly ministry?: MinistryType;
  readonly role?: UserRole;
  readonly personActive?: boolean;
  readonly accountExists?: boolean;
  readonly accountEnabled?: boolean;
  readonly page: number;
  readonly size: number;
}

export interface PersonRoleUpdateRequest {
  readonly role: UserRole;
}

export interface PersonRoleUpdateResponse {
  readonly id: number;
  readonly name: string;
  readonly phoneNumber: string;
  readonly ministries: MinistryType[];
  readonly roles: UserRole[];
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

export interface PersonMinistriesResponse {
  readonly id: number;
  readonly ministries: MinistryType[];
}

export interface PersonMinistriesUpdateRequest {
  readonly ministries: MinistryType[];
}
