import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  if (!token) {
    return next(request);
  }
  return next(request.clone({
    setHeaders: { Authorization: `Bearer ${token}` }
  })).pipe(
    catchError(error => {
      // A rejected JWT cannot recover while it remains in browser storage.
      // Clearing it immediately returns the UI to login for a fresh session.
      if (error.status === 401) {
        auth.logout();
      }
      return throwError(() => error);
    })
  );
};
