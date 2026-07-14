import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { API_BASE_URL } from './api.config';
import { AuthSessionService } from './auth-session.service';
import { LoginRequest, TokenResponse } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpTestingController: HttpTestingController;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let router: jasmine.SpyObj<Router>;

  const loginRequest: LoginRequest = {
    username: '11999999999',
    password: 'secret',
  };

  const tokenResponse: TokenResponse = {
    access_token: 'access-token-value',
    token_type: 'Bearer',
    expires_in: 86400,
  };

  beforeEach(() => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', ['clear']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should post login credentials to the public login endpoint', () => {
    service.login(loginRequest).subscribe((response) => {
      expect(response).toEqual(tokenResponse);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/public/login`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(loginRequest);

    request.flush(tokenResponse);
  });

  it('should propagate HTTP errors', (done) => {
    service.login(loginRequest).subscribe({
      next: () => {
        fail('Expected login request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/public/login`);
    request.flush(
      { error: 'Invalid credentials' },
      {
        status: 401,
        statusText: 'Unauthorized',
      },
    );
  });

  it('should clear the session when logging out', () => {
    service.logout();

    expect(authSessionService.clear).toHaveBeenCalledOnceWith();
  });

  it('should navigate to login when logging out', () => {
    service.logout();

    expect(router.navigate).toHaveBeenCalledOnceWith(['/login']);
  });

  it('should clear the session before navigating on logout', () => {
    const calls: string[] = [];
    authSessionService.clear.and.callFake(() => {
      calls.push('clear');
    });
    router.navigate.and.callFake(() => {
      calls.push('navigate');
      return Promise.resolve(true);
    });

    service.logout();

    expect(calls).toEqual(['clear', 'navigate']);
  });

  it('should not make an HTTP request when logging out', () => {
    service.logout();

    const logoutRequests = httpTestingController.match(`${API_BASE_URL}/public/logout`);

    expect(logoutRequests).toHaveSize(0);
  });
});
