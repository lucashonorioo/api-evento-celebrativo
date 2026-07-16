export interface PersonManagementFormValue {
  name: string;
  phoneNumber: string;
  birthdayDate: string;
  password: string;
}

export function normalizePersonManagementRequest<T extends PersonManagementFormValue>(
  value: T,
): T {
  return {
    ...value,
    name: value.name.trim(),
    phoneNumber: value.phoneNumber.trim(),
  };
}

export function todayLocalDate(): string {
  const today = new Date();
  const month = `${today.getMonth() + 1}`.padStart(2, '0');
  const day = `${today.getDate()}`.padStart(2, '0');

  return `${today.getFullYear()}-${month}-${day}`;
}
