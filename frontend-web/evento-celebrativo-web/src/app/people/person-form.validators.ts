import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

import { todayLocalDate } from './person-form.helpers';

export const PERSON_PHONE_NUMBER_LENGTH = 11;
export const PERSON_PASSWORD_MIN_LENGTH = 6;

export function notBlankValidator(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length === 0
    ? { blank: true }
    : null;
}

export function pastDateValidator(control: AbstractControl): ValidationErrors | null {
  if (typeof control.value !== 'string' || control.value.length === 0) {
    return null;
  }

  return control.value < todayLocalDate() ? null : { pastDate: true };
}

export function personPhoneNumberValidators(): ValidatorFn[] {
  return [
    Validators.required,
    Validators.minLength(PERSON_PHONE_NUMBER_LENGTH),
    Validators.maxLength(PERSON_PHONE_NUMBER_LENGTH),
  ];
}

export function personPasswordValidators(): ValidatorFn[] {
  return [Validators.required, Validators.minLength(PERSON_PASSWORD_MIN_LENGTH)];
}
