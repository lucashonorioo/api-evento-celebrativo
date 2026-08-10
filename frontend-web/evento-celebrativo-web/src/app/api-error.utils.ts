export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function apiErrorCode(value: unknown): string | null {
  if (!isRecord(value)) {
    return null;
  }

  const errorCode = value['errorCode'];

  return typeof errorCode === 'string' ? errorCode : null;
}

export function apiErrorMessage(value: unknown): string | null {
  if (!isRecord(value)) {
    return null;
  }

  const message = value['error'];

  return typeof message === 'string' ? message : null;
}
