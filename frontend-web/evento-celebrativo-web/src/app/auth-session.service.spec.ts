import { TestBed } from '@angular/core/testing';

import { AuthSessionService } from './auth-session.service';
import { JwtPayload, TokenResponse } from './auth.models';

describe('AuthSessionService', () => {
  let service: AuthSessionService;

  const tokenResponse: TokenResponse = {
    access_token: 'access-token-value',
    token_type: 'Bearer',
    expires_in: 86400,
  };
  const currentTimeSeconds = 2000;

  function createToken(payload: unknown): string {
    return [
      encodeBase64Url({ alg: 'none', typ: 'JWT' }),
      encodeBase64Url(payload),
      'signature',
    ].join('.');
  }

  function createTokenWithPayloadText(payloadText: string): string {
    return [
      encodeBase64Url({ alg: 'none', typ: 'JWT' }),
      toBase64Url(btoa(payloadText)),
      'signature',
    ].join('.');
  }

  function encodeBase64Url(value: unknown): string {
    return toBase64Url(btoa(JSON.stringify(value)));
  }

  function toBase64Url(value: string): string {
    return value.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  }

  function saveAccessToken(accessToken: string): void {
    service.saveToken({
      ...tokenResponse,
      access_token: accessToken,
    });
  }

  function createValidPayload(exp = currentTimeSeconds + 60): JwtPayload {
    return {
      username: '11999999999',
      authorities: ['ROLE_ADMIN', 'ROLE_OPERATOR'],
      exp,
    };
  }

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

  it('should decode a valid JWT payload', () => {
    const payload = createValidPayload();
    saveAccessToken(createToken(payload));

    expect(service.getPayload()).toEqual(payload);
    expect(service.getUsername()).toBe(payload.username);
    expect(service.getAuthorities()).toEqual(payload.authorities);
  });

  it('should identify a valid non-expired session', () => {
    spyOn(Date, 'now').and.returnValue(currentTimeSeconds * 1000);
    saveAccessToken(createToken(createValidPayload()));

    expect(service.hasValidSession()).toBeTrue();
    expect(service.isTokenExpired()).toBeFalse();
  });

  it('should identify an expired token', () => {
    spyOn(Date, 'now').and.returnValue(currentTimeSeconds * 1000);
    saveAccessToken(createToken(createValidPayload(currentTimeSeconds - 1)));

    expect(service.hasValidSession()).toBeFalse();
    expect(service.isTokenExpired()).toBeTrue();
  });

  it('should consider a token expired when exp matches the current time', () => {
    spyOn(Date, 'now').and.returnValue(currentTimeSeconds * 1000);
    saveAccessToken(createToken(createValidPayload(currentTimeSeconds)));

    expect(service.hasValidSession()).toBeFalse();
    expect(service.isTokenExpired()).toBeTrue();
  });

  it('should return safe defaults when there is no token', () => {
    expect(service.getPayload()).toBeNull();
    expect(service.hasValidSession()).toBeFalse();
    expect(service.isTokenExpired()).toBeTrue();
    expect(service.getUsername()).toBeNull();
    expect(service.getAuthorities()).toEqual([]);
  });

  it('should reject malformed tokens without throwing errors', () => {
    const malformedTokens = [
      'texto-sem-pontos',
      'header.payload',
      'header.payload.signature.extra',
    ];

    malformedTokens.forEach((token) => {
      saveAccessToken(token);

      expect(service.getPayload()).toBeNull();
      expect(service.hasValidSession()).toBeFalse();
      expect(service.isTokenExpired()).toBeTrue();
    });
  });

  it('should reject invalid Base64 payload without throwing errors', () => {
    saveAccessToken('header.!!!!.signature');

    expect(service.getPayload()).toBeNull();
    expect(service.hasValidSession()).toBeFalse();
  });

  it('should reject invalid JSON payload without throwing errors', () => {
    saveAccessToken(createTokenWithPayloadText('not-json'));

    expect(service.getPayload()).toBeNull();
    expect(service.hasValidSession()).toBeFalse();
  });

  it('should reject payload without exp', () => {
    saveAccessToken(
      createToken({
        username: '11999999999',
        authorities: ['ROLE_ADMIN'],
      }),
    );

    expect(service.getPayload()).toBeNull();
  });

  it('should reject payload with exp as string', () => {
    saveAccessToken(
      createToken({
        username: '11999999999',
        authorities: ['ROLE_ADMIN'],
        exp: '2000',
      }),
    );

    expect(service.getPayload()).toBeNull();
  });

  it('should reject payload without username', () => {
    saveAccessToken(
      createToken({
        authorities: ['ROLE_ADMIN'],
        exp: currentTimeSeconds + 60,
      }),
    );

    expect(service.getPayload()).toBeNull();
  });

  it('should reject payload with authorities that are not an array', () => {
    saveAccessToken(
      createToken({
        username: '11999999999',
        authorities: 'ROLE_ADMIN',
        exp: currentTimeSeconds + 60,
      }),
    );

    expect(service.getPayload()).toBeNull();
  });

  it('should reject payload with non-string authorities', () => {
    saveAccessToken(
      createToken({
        username: '11999999999',
        authorities: ['ROLE_ADMIN', 123],
        exp: currentTimeSeconds + 60,
      }),
    );

    expect(service.getPayload()).toBeNull();
  });

  it('should return a copy of the authorities collection', () => {
    saveAccessToken(createToken(createValidPayload()));

    const authorities = service.getAuthorities();
    const payload = service.getPayload() as JwtPayload;

    expect(authorities).toEqual(['ROLE_ADMIN', 'ROLE_OPERATOR']);
    expect(authorities).not.toBe(payload.authorities);
  });
});
