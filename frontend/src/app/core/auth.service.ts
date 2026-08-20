import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { catchError, finalize, Observable, of, shareReplay, tap } from 'rxjs';

export interface AuthResponse {
  expiresIn: number;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface PasswordResetResponse {
  message: string;
  developmentResetToken: string | null;
  expiresAt: string | null;
}

/**
 * Owns browser-visible account state. The backend stores the JWT in an
 * HttpOnly cookie, so Angular cannot read or accidentally expose it.
 *
 * Browser-visible metadata is restored from the protected session endpoint.
 * The HttpOnly cookie remains the only durable authentication credential.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly session = signal<AuthResponse | null>(null);
  readonly sessionChecked = signal(false);
  private restoreRequest?: Observable<AuthResponse | null>;

  constructor(private readonly http: HttpClient) {}

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap(session => this.setSession(session))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', request).pipe(
      tap(session => this.setSession(session))
    );
  }

  restoreSession(): Observable<AuthResponse | null> {
    if (this.sessionChecked()) {
      return of(this.session());
    }
    if (this.restoreRequest) {
      return this.restoreRequest;
    }
    this.restoreRequest = this.http.get<AuthResponse>('/api/auth/session').pipe(
      tap(session => this.session.set(session)),
      catchError(() => {
        this.session.set(null);
        return of(null);
      }),
      finalize(() => {
        this.sessionChecked.set(true);
        this.restoreRequest = undefined;
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    return this.restoreRequest;
  }

  requestPasswordReset(email: string): Observable<PasswordResetResponse> {
    return this.http.post<PasswordResetResponse>('/api/auth/password-reset/request', { email });
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>('/api/auth/password-reset/confirm', { token, password });
  }

  isAuthenticated(): boolean {
    return this.session() !== null;
  }

  logout(): void {
    this.http.post<void>('/api/auth/logout', {}).subscribe({
      error: () => this.clearSession()
    });
    this.clearSession();
  }

  clearSession(): void {
    this.session.set(null);
    this.sessionChecked.set(true);
  }

  private setSession(session: AuthResponse): void {
    this.session.set(session);
    this.sessionChecked.set(true);
  }
}
