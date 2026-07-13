import { Injectable } from '@angular/core';

import { TokenResponse } from './auth.models';

const ACCESS_TOKEN_KEY = 'access_token';
const TOKEN_TYPE_KEY = 'token_type';

@Injectable({
  providedIn: 'root',
})
export class AuthSessionService {
  saveToken(response: TokenResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, response.access_token);
    localStorage.setItem(TOKEN_TYPE_KEY, response.token_type);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getTokenType(): string | null {
    return localStorage.getItem(TOKEN_TYPE_KEY);
  }

  hasToken(): boolean {
    return this.getAccessToken() !== null;
  }

  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(TOKEN_TYPE_KEY);
  }
}
