import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { API_BASE_URL } from './api.config';
import { AuthSessionService } from './auth-session.service';
import { LoginRequest, TokenResponse } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpTestingController: HttpTestingController;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let router: Router;
  let navigateSpy: jasmine.Spy;

  beforeEach(() => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', ['clear']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    });

    service = TestBed.inject(AuthService);
    httpTestingController = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should POST credentials to the public login endpoint and return the token response', () => {
    const credentials: LoginRequest = { username: '34999999999', password: 'senha123' };
    const response: TokenResponse = {
      access_token: 'token-abc',
      token_type: 'Bearer',
      expires_in: 3600,
    };
    let received: TokenResponse | undefined;

    service.login(credentials).subscribe((result) => {
      received = result;
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/public/login`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(credentials);

    request.flush(response);

    expect(received).toEqual(response);
  });

  it('should propagate login errors without clearing the session', () => {
    let receivedError: unknown;

    service.login({ username: 'x', password: 'y' }).subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/public/login`);
    request.flush({ message: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toBeTruthy();
    expect(authSessionService.clear).not.toHaveBeenCalled();
  });

  it('should clear the session and navigate to login on logout', () => {
    service.logout();

    expect(authSessionService.clear).toHaveBeenCalledOnceWith();
    expect(navigateSpy).toHaveBeenCalledOnceWith(['/login']);
  });
});
