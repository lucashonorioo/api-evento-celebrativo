import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { LoginRequest } from '../auth.models';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, CommonModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  phone: string = '';
  password: string = '';
  errorMessage: string = '';

  constructor(
    private readonly authService: AuthService,
    private readonly authSessionService: AuthSessionService,
    private readonly router: Router,
  ) {}

  onSubmit(): void {
    this.errorMessage = '';

    const request: LoginRequest = {
      username: this.phone,
      password: this.password,
    };

    this.authService.login(request).subscribe({
      next: (response) => {
        this.authSessionService.saveToken(response);

        void this.router.navigate(['/inicio']);
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.errorMessage = 'Credenciais inválidas. Por favor, tente novamente.';
        } else {
          this.errorMessage = 'Ocorreu um erro no servidor. Tente mais tarde.';
        }
      },
    });
  }
}
