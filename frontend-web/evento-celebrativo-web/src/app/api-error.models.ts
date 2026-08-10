export interface ApiErrorBody {
  readonly timestamp?: string;
  readonly status?: number;
  readonly error?: string;
  readonly errorCode?: string;
  readonly path?: string;
}
