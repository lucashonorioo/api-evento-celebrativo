import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  imports: [FormsModule, CommonModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  phone: string = '';
  password: string = '';
  errorMessage: string = '';

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit(){
    this.errorMessage = '';

    this.authService.login({phone: this.phone, password: this.password}).subscribe({
      next: (response) => {
        console.log('Login bem-sucedido!', response);

        localStorage.setItem('access_token', response.access_token);
        localStorage.setItem('token_type', response.token_type);

        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        console.error('Erro de login:', error);
        if (error.status === 401) {
            this.errorMessage = 'Credenciais inválidas. Por favor, tente novamente.';
        } else {
            this.errorMessage = 'Ocorreu um erro no servidor. Tente mais tarde.';
        }
      }
    });
  }
}
