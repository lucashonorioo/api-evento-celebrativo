import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { AuthSessionService } from './auth-session.service';

const LOGIN_URL = `${API_BASE_URL}/public/login`;

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authSessionService = inject(AuthSessionService);
  const router = inject(Router);
  const isApiRequest = request.url.startsWith(`${API_BASE_URL}/`);
  const isLoginRequest = request.url === LOGIN_URL;
  const shouldAddToken =
    isApiRequest &&
    !isLoginRequest &&
    !request.headers.has('Authorization') &&
    authSessionService.hasValidSession();
  const token = shouldAddToken ? authSessionService.getAccessToken() : null;
  const handledRequest =
    token === null
      ? request
      : request.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        });

  return next(handledRequest).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        isApiRequest &&
        !isLoginRequest &&
        error.status === 401
      ) {
        authSessionService.clear();

        if (router.url !== '/login') {
          void router.navigate(['/login']);
        }
      }

      return throwError(() => error);
    }),
  );
};
