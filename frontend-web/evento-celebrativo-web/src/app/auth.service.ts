import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { AuthSessionService } from './auth-session.service';
import { LoginRequest, TokenResponse } from './auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly loginUrl = `${API_BASE_URL}/public/login`;

  constructor(
    private readonly http: HttpClient,
    private readonly authSessionService: AuthSessionService,
    private readonly router: Router,
  ) {}

  login(credentials: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(this.loginUrl, credentials);
  }

  logout(): void {
    this.authSessionService.clear();
    void this.router.navigate(['/login']);
  }
}
