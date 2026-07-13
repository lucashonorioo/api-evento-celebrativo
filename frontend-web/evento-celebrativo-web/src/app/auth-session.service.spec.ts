import { TestBed } from '@angular/core/testing';

import { AuthSessionService } from './auth-session.service';
import { TokenResponse } from './auth.models';

describe('AuthSessionService', () => {
  let service: AuthSessionService;

  const tokenResponse: TokenResponse = {
    access_token: 'access-token-value',
    token_type: 'Bearer',
    expires_in: 86400,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthSessionService);
    service.clear();
    localStorage.removeItem('unrelated_key');
  });

  afterEach(() => {
    service.clear();
    localStorage.removeItem('unrelated_key');
  });

  it('should store and retrieve the access token', () => {
    service.saveToken(tokenResponse);

    expect(service.getAccessToken()).toBe(tokenResponse.access_token);
  });

  it('should store and retrieve the token type', () => {
    service.saveToken(tokenResponse);

    expect(service.getTokenType()).toBe(tokenResponse.token_type);
  });

  it('should return null when there is no access token', () => {
    expect(service.getAccessToken()).toBeNull();
  });

  it('should identify when a token exists', () => {
    expect(service.hasToken()).toBeFalse();

    service.saveToken(tokenResponse);

    expect(service.hasToken()).toBeTrue();
  });

  it('should remove only authentication data', () => {
    localStorage.setItem('unrelated_key', 'keep-me');
    service.saveToken(tokenResponse);

    service.clear();

    expect(service.getAccessToken()).toBeNull();
    expect(service.getTokenType()).toBeNull();
    expect(localStorage.getItem('unrelated_key')).toBe('keep-me');
  });
});
