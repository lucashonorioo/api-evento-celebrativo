import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthSessionService } from './auth-session.service';

export const guestGuard: CanActivateFn = () => {
  const authSessionService = inject(AuthSessionService);
  const router = inject(Router);

  if (authSessionService.hasValidSession()) {
    return router.createUrlTree(['/app/inicio']);
  }

  return true;
};
