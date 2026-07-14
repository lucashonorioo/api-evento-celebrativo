import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { API_BASE_URL } from './api.config';
import { AuthSessionService } from './auth-session.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let navigateSpy: jasmine.Spy<(commands: unknown[]) => Promise<boolean>>;
  let currentRouterUrl: string;

  beforeEach(() => {
    currentRouterUrl = '/app/inicio';
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'clear',
      'getAccessToken',
      'hasValidSession',
    ]);
    navigateSpy = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));

    const routerMock: Pick<Router, 'navigate' | 'url'> = {
      navigate: navigateSpy,
      get url() {
        return currentRouterUrl;
      },
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: Router, useValue: routerMock },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should add Authorization header to API requests when the session is valid', () => {
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.get(`${API_BASE_URL}/locais`).subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);

    expect(request.request.headers.get('Authorization')).toBe('Bearer token-de-teste');

    request.flush([]);
  });

  it('should not add Authorization header when the session is invalid', () => {
    authSessionService.hasValidSession.and.returnValue(false);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.get(`${API_BASE_URL}/locais`).subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);

    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush([]);
  });

  it('should not add Authorization header to login requests', () => {
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.post(`${API_BASE_URL}/public/login`, {}).subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/public/login`);

    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush({});
  });

  it('should not add Authorization header to external requests', () => {
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.get('https://example.com/resource').subscribe();

    const request = httpTestingController.expectOne('https://example.com/resource');

    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush({});
  });

  it('should preserve an existing Authorization header', () => {
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient
      .get(`${API_BASE_URL}/locais`, {
        headers: {
          Authorization: 'Custom value',
        },
      })
      .subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);

    expect(request.request.headers.get('Authorization')).toBe('Custom value');

    request.flush([]);
  });

  it('should clear the session and redirect to login after a 401 from an authenticated endpoint', () => {
    let receivedError: unknown;
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.get(`${API_BASE_URL}/locais`).subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);
    request.flush(
      { message: 'Unauthorized' },
      {
        status: 401,
        statusText: 'Unauthorized',
      },
    );

    expect(authSessionService.clear).toHaveBeenCalledOnceWith();
    expect(navigateSpy).toHaveBeenCalledOnceWith(['/login']);
    expect(receivedError).toEqual(jasmine.any(HttpErrorResponse));
  });

  it('should preserve login 401 handling for the subscriber', () => {
    let receivedError: unknown;
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.post(`${API_BASE_URL}/public/login`, {}).subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/public/login`);
    request.flush(
      { message: 'Invalid credentials' },
      {
        status: 401,
        statusText: 'Unauthorized',
      },
    );

    expect(authSessionService.clear).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(receivedError).toEqual(jasmine.any(HttpErrorResponse));
  });

  it('should propagate 403 responses without clearing the session or redirecting', () => {
    let receivedError: unknown;
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.get(`${API_BASE_URL}/pessoas/1/roles`).subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/1/roles`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );

    expect(authSessionService.clear).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(receivedError).toEqual(jasmine.any(HttpErrorResponse));
  });

  it('should not navigate again after a 401 when the user is already on login', () => {
    currentRouterUrl = '/login';
    authSessionService.hasValidSession.and.returnValue(true);
    authSessionService.getAccessToken.and.returnValue('token-de-teste');

    httpClient.get(`${API_BASE_URL}/locais`).subscribe({
      error: () => undefined,
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);
    request.flush(
      { message: 'Unauthorized' },
      {
        status: 401,
        statusText: 'Unauthorized',
      },
    );

    expect(authSessionService.clear).toHaveBeenCalledOnceWith();
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
