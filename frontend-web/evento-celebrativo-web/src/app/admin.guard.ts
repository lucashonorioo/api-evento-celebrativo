import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthSessionService } from './auth-session.service';

export const adminGuard: CanActivateFn = () => {
  const authSessionService = inject(AuthSessionService);
  const router = inject(Router);

  if (!authSessionService.hasValidSession()) {
    return router.createUrlTree(['/login']);
  }

  if (authSessionService.hasAuthority('ROLE_ADMIN')) {
    return true;
  }

  return router.createUrlTree(['/app/acesso-negado']);
};
