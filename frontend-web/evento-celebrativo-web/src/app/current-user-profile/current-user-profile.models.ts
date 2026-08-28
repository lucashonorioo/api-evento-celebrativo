import { UserRole } from '../admin-users/admin-user.models';
import { MinistryType } from '../legacy-ministry-type.model';

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
