import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  return next(request).pipe(
    catchError(error => {
      // A 401 means the protected cookie is absent or expired. Clear only the
      // browser metadata; calling logout here would cause another HTTP request.
      if (error.status === 401) {
        auth.clearSession();
      }
      return throwError(() => error);
    })
  );
};
