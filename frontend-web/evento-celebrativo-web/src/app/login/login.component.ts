import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { LoginRequest } from '../auth.models';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, CommonModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  phone: string = '';
  password: string = '';
  errorMessage: string = '';
  isSubmitting: boolean = false;
  isPasswordVisible: boolean = false;
  readonly currentYear = new Date().getFullYear();

  constructor(
    private readonly authService: AuthService,
    private readonly authSessionService: AuthSessionService,
    private readonly router: Router,
  ) {}

  get passwordInputType(): 'password' | 'text' {
    return this.isPasswordVisible ? 'text' : 'password';
  }

  get passwordToggleLabel(): string {
    return this.isPasswordVisible ? 'Ocultar senha' : 'Mostrar senha';
  }

  onSubmit(form?: NgForm): void {
    this.errorMessage = '';

    if (this.isSubmitting) {
      return;
    }

    if (form?.invalid) {
      form.form.markAllAsTouched();

      return;
    }

    const request: LoginRequest = {
      username: this.phone,
      password: this.password,
    };

    this.isSubmitting = true;

    this.authService.login(request).subscribe({
      next: (response) => {
        this.authSessionService.saveToken(response);

        void this.router.navigate(['/app/inicio']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;

        this.errorMessage = loginErrorMessageFor(error);
      },
    });
  }

  togglePasswordVisibility(): void {
    this.isPasswordVisible = !this.isPasswordVisible;
  }
}

function loginErrorMessageFor(error: unknown): string {
  return isInvalidCredentialsError(error)
    ? 'Usuário ou senha inválidos.'
    : 'Não foi possível acessar o sistema no momento. Tente novamente mais tarde.';
}

function isInvalidCredentialsError(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return false;
  }

  if (error.status === 401) {
    return true;
  }

  return error.status === 400 && hasOAuthErrorCode(error.error, 'invalid_grant');
}

function hasOAuthErrorCode(value: unknown, expectedCode: string): boolean {
  if (typeof value === 'string') {
    return hasOAuthErrorCode(parseJson(value), expectedCode);
  }

  if (!isRecord(value)) {
    return false;
  }

  const error = value['error'];

  if (error === expectedCode) {
    return true;
  }

  return typeof error === 'string' && hasOAuthErrorCode(parseJson(error), expectedCode);
}

function parseJson(value: string): unknown {
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
