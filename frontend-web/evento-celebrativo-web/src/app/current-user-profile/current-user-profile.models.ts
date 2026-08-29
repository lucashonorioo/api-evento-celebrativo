import { MinistryType, UserRole } from '../admin-users/admin-user.models';

export interface CurrentUserProfile {
  readonly id: number;
  readonly name: string;
  readonly phoneNumber: string;
  readonly birthdayDate: string;
  readonly roles: UserRole[];
  readonly ministries: MinistryType[];
}

export interface CurrentUserProfileUpdateRequest {
  readonly name: string;
  readonly birthdayDate: string;
}
