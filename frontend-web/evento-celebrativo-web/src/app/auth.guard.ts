import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthSessionService } from './auth-session.service';

export const authGuard: CanActivateFn = () => {
  const authSessionService = inject(AuthSessionService);
  const router = inject(Router);

  if (authSessionService.hasValidSession()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};
