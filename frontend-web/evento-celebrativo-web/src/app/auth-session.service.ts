import { Injectable } from '@angular/core';

import { JwtPayload, TokenResponse } from './auth.models';

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

  getPayload(): JwtPayload | null {
    const token = this.getAccessToken();

    if (token === null) {
      return null;
    }

    return this.decodePayload(token);
  }

  isTokenExpired(): boolean {
    const payload = this.getPayload();

    if (payload === null) {
      return true;
    }

    return Date.now() >= payload.exp * 1000;
  }

  hasValidSession(): boolean {
    const payload = this.getPayload();

    return payload !== null && Date.now() < payload.exp * 1000;
  }

  getUsername(): string | null {
    return this.getPayload()?.username ?? null;
  }

  getAuthorities(): readonly string[] {
    const payload = this.getPayload();

    return payload === null ? [] : [...payload.authorities];
  }

  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(TOKEN_TYPE_KEY);
  }

  private decodePayload(token: string): JwtPayload | null {
    const parts = token.split('.');

    if (parts.length !== 3 || parts[1] === '') {
      return null;
    }

    try {
      const payloadText = this.decodeBase64Url(parts[1]);
      const parsedPayload: unknown = JSON.parse(payloadText);

      return this.isJwtPayload(parsedPayload) ? parsedPayload : null;
    } catch {
      return null;
    }
  }

  private decodeBase64Url(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));

    return new TextDecoder().decode(bytes);
  }

  private isJwtPayload(value: unknown): value is JwtPayload {
    if (typeof value !== 'object' || value === null) {
      return false;
    }

    const payload = value as Record<string, unknown>;
    const username = payload['username'];
    const authorities = payload['authorities'];
    const exp = payload['exp'];

    return (
      typeof username === 'string' &&
      username.trim().length > 0 &&
      Array.isArray(authorities) &&
      authorities.every((authority) => typeof authority === 'string') &&
      typeof exp === 'number' &&
      Number.isFinite(exp) &&
      exp > 0
    );
  }
}
