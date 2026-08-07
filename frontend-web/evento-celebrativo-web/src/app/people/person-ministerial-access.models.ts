export const MINISTERIAL_ACCESS_ROLE = 'ROLE_OPERATOR' as const;

export interface PersonMinisterialCadastralFields {
  readonly name: string;
  readonly phoneNumber: string;
  readonly birthdayDate: string;
}

export type PersonMinisterialUpdateRequest = PersonMinisterialCadastralFields;

export type PersonMinisterialCreateRequest =
  | (PersonMinisterialCadastralFields & {
      readonly createAccess: false;
    })
  | (PersonMinisterialCadastralFields & {
      readonly createAccess: true;
      readonly password: string;
      readonly accessRole: typeof MINISTERIAL_ACCESS_ROLE;
    });
