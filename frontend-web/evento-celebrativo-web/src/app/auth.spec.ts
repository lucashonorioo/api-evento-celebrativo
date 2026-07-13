import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LoginRequest, TokenResponse } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpTestingController: HttpTestingController;

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
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
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

    const request = httpTestingController.expectOne('http://localhost:8080/public/login');

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

    const request = httpTestingController.expectOne('http://localhost:8080/public/login');
    request.flush(
      { error: 'Invalid credentials' },
      {
        status: 401,
        statusText: 'Unauthorized',
      },
    );
  });
});
