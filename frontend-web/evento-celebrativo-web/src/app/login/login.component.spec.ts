import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

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

  it('should render the real login fields', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('label[for="phone"]')?.textContent).toContain('Telefone');
    expect(compiled.querySelector('label[for="password"]')?.textContent).toContain('Senha');
    expect(compiled.querySelector<HTMLInputElement>('#phone')?.autocomplete).toBe('username');
    expect(compiled.querySelector<HTMLInputElement>('#password')?.autocomplete).toBe(
      'current-password',
    );
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

  it('should not call login when the form is invalid', () => {
    const form = {
      invalid: true,
      form: jasmine.createSpyObj('FormGroup', ['markAllAsTouched']),
    } as unknown as NgForm;

    component.onSubmit(form);

    expect(authService.login).not.toHaveBeenCalled();
    expect(form.form.markAllAsTouched).toHaveBeenCalled();
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

  it('should show the loading state while login is pending', () => {
    const loginResponse = new Subject<TokenResponse>();
    authService.login.and.returnValue(loginResponse.asObservable());
    component.phone = '11999999999';
    component.password = 'secret';

    component.onSubmit();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const submitButton = compiled.querySelector<HTMLButtonElement>('.login-submit');

    expect(component.isSubmitting).toBeTrue();
    expect(submitButton?.disabled).toBeTrue();
    expect(submitButton?.textContent).toContain('Entrando...');
  });

  it('should prevent duplicated submissions while login is pending', () => {
    const loginResponse = new Subject<TokenResponse>();
    authService.login.and.returnValue(loginResponse.asObservable());
    component.phone = '11999999999';
    component.password = 'secret';

    component.onSubmit();
    component.onSubmit();

    expect(authService.login).toHaveBeenCalledTimes(1);
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
    expect(component.isSubmitting).toBeFalse();
  });

  it('should not access localStorage directly', () => {
    const setItemSpy = spyOn(Storage.prototype, 'setItem');
    authService.login.and.returnValue(of(tokenResponse));

    component.onSubmit();

    expect(setItemSpy).not.toHaveBeenCalled();
  });

  it('should show and hide the password without changing the typed value', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const passwordInput = compiled.querySelector<HTMLInputElement>('#password');
    const toggleButton = compiled.querySelector<HTMLButtonElement>('.password-toggle');

    passwordInput!.value = 'secret';
    passwordInput!.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(passwordInput?.type).toBe('password');
    expect(toggleButton?.getAttribute('aria-label')).toBe('Mostrar senha');

    toggleButton?.click();
    fixture.detectChanges();

    expect(passwordInput?.type).toBe('text');
    expect(component.password).toBe('secret');
    expect(passwordInput?.value).toBe('secret');
    expect(toggleButton?.getAttribute('aria-label')).toBe('Ocultar senha');
    expect(toggleButton?.getAttribute('aria-pressed')).toBe('true');
  });

  it('should render an accessible password toggle button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const toggleButton = compiled.querySelector<HTMLButtonElement>('.password-toggle');

    expect(toggleButton?.type).toBe('button');
    expect(toggleButton?.getAttribute('aria-label')).toBe('Mostrar senha');
    expect(toggleButton?.getAttribute('aria-pressed')).toBe('false');
  });

  it('should omit unsupported login actions', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).not.toContain('Lembrar-me');
    expect(text).not.toContain('Esqueci minha senha');
    expect(text).not.toContain('Entrar com token');
    expect(text).not.toContain('ou continue com');
  });

  it('should render the dynamic current year in the footer', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const footer = compiled.querySelector<HTMLElement>('.login-footer');

    expect(footer?.textContent).toContain(`${new Date().getFullYear()}`);
    expect(footer?.textContent).toContain('Evento Celebrativo. Todos os direitos reservados.');
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
