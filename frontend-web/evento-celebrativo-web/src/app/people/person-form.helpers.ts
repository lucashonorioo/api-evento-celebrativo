import {
  MINISTERIAL_ACCESS_ROLE,
  PersonMinisterialCreateRequest,
  PersonMinisterialUpdateRequest,
} from './person-ministerial-access.models';

export interface PersonMinisterialFormValue {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  createAccess: boolean;
  password: string;
  confirmPassword: string;
}

export function buildPersonMinisterialCreateRequest(
  value: PersonMinisterialFormValue,
): PersonMinisterialCreateRequest {
  const cadastralFields = {
    name: value.name.trim(),
    phoneNumber: value.phoneNumber.trim(),
    birthdayDate: value.birthdayDate,
  };

  if (!value.createAccess) {
    return { ...cadastralFields, createAccess: false };
  }

  return {
    ...cadastralFields,
    createAccess: true,
    password: value.password,
    accessRole: MINISTERIAL_ACCESS_ROLE,
  };
}

export function buildPersonMinisterialUpdateRequest(
  value: Pick<PersonMinisterialFormValue, 'name' | 'phoneNumber' | 'birthdayDate'>,
): PersonMinisterialUpdateRequest {
  return {
    name: value.name.trim(),
    phoneNumber: value.phoneNumber.trim(),
    birthdayDate: value.birthdayDate,
  };
}

export function todayLocalDate(): string {
  const today = new Date();
  const month = `${today.getMonth() + 1}`.padStart(2, '0');
  const day = `${today.getDate()}`.padStart(2, '0');

  return `${today.getFullYear()}-${month}-${day}`;
}
