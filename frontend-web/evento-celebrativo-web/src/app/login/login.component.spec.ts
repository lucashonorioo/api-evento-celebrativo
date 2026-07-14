import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../auth-session.service';
import { TokenResponse } from '../auth.models';
import { AuthService } from '../auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let router: Router;
  let navigateSpy: jasmine.Spy;

  const tokenResponse: TokenResponse = {
    access_token: 'access-token-value',
    token_type: 'Bearer',
    expires_in: 86400,
  };

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'saveToken',
    ]);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should send phone as username and the password', () => {
    authService.login.and.returnValue(of(tokenResponse));
    component.phone = '11999999999';
    component.password = 'secret';

    component.onSubmit();

    expect(authService.login).toHaveBeenCalledOnceWith({
      username: '11999999999',
      password: 'secret',
    });
  });

  it('should store the token after a successful login', () => {
    authService.login.and.returnValue(of(tokenResponse));

    component.onSubmit();

    expect(authSessionService.saveToken).toHaveBeenCalledOnceWith(tokenResponse);
  });

  it('should navigate to the current post-login route after a successful login', () => {
    authService.login.and.returnValue(of(tokenResponse));

    component.onSubmit();

    expect(navigateSpy).toHaveBeenCalledOnceWith(['/app/inicio']);
  });

  it('should not store the token when login fails', () => {
    authService.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' })),
    );

    component.onSubmit();

    expect(authSessionService.saveToken).not.toHaveBeenCalled();
  });

  it('should show an invalid credentials message after a 401 error', () => {
    authService.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' })),
    );

    component.onSubmit();

    expect(component.errorMessage).toBe('Credenciais inválidas. Por favor, tente novamente.');
  });

  it('should not access localStorage directly', () => {
    const setItemSpy = spyOn(Storage.prototype, 'setItem');
    authService.login.and.returnValue(of(tokenResponse));

    component.onSubmit();

    expect(setItemSpy).not.toHaveBeenCalled();
  });

  it('should render a public events link', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll('.login-secondary-link'));
    const linkTargets = links.map((link) => link.getAttribute('href'));
    const linkTexts = links.map((link) => link.textContent);

    expect(linkTargets).toContain('/eventos');
    expect(linkTexts.join(' ')).toContain('Consultar eventos');
  });

  it('should render a public Eucharist schedule link', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll('.login-secondary-link'));
    const linkTargets = links.map((link) => link.getAttribute('href'));
    const linkTexts = links.map((link) => link.textContent);

    expect(linkTargets).toContain('/escala/eucaristia');
    expect(linkTexts.join(' ')).toContain('Consultar escala de Eucaristia');
  });
});
