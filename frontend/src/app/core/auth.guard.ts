import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const resetToken = router.parseUrl(state.url).queryParams['resetToken'];
  if (typeof resetToken === 'string' && resetToken) {
    return router.createUrlTree(['/login'], { queryParams: { resetToken } });
  }
  return auth.restoreSession().pipe(
    map(session => session
      ? true
      : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }))
  );
};

export const guestGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (route.queryParamMap.has('resetToken')) {
    return true;
  }
  return auth.restoreSession().pipe(
    map(session => session ? router.createUrlTree(['/dashboard']) : true)
  );
};
