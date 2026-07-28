import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { CurrentUserProfile, CurrentUserProfileUpdateRequest } from './current-user-profile.models';

@Injectable({
  providedIn: 'root',
})
export class CurrentUserProfileService {
  private readonly http = inject(HttpClient);

  private readonly profileUrl = `${API_BASE_URL}/pessoas/me`;

  readonly profile = signal<CurrentUserProfile | null>(null);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  loadProfile(): void {
    if (this.isLoading()) {
      return;
    }

    this.profile.set(null);
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.http.get<CurrentUserProfile>(this.profileUrl).subscribe({
      next: (profile) => {
        this.isLoading.set(false);
        this.profile.set(profile);
      },
      error: (error: unknown) => {
        this.isLoading.set(false);
        this.errorMessage.set(loadProfileErrorMessageFor(error));
      },
    });
  }

  updateProfile(request: CurrentUserProfileUpdateRequest): Observable<CurrentUserProfile> {
    return this.http
      .put<CurrentUserProfile>(this.profileUrl, request)
      .pipe(tap((profile) => this.profile.set(profile)));
  }

  clearProfile(): void {
    this.profile.set(null);
    this.isLoading.set(false);
    this.errorMessage.set(null);
  }
}

function loadProfileErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return 'Não foi possível acessar o seu perfil.';
    }

    if (error.status === 404) {
      return 'Não foi possível localizar o perfil associado à sua conta autenticada.';
    }
  }

  return 'Não foi possível carregar o seu perfil. Tente novamente.';
}
