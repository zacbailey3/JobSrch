import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

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
 * Local storage contains display metadata only; it is not proof that the
 * server-side cookie is still valid. Any 401 response clears this state.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'jobsrch-session';
  readonly session = signal<AuthResponse | null>(this.readSession());

  constructor(private readonly http: HttpClient) {}

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap(session => this.storeSession(session))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', request).pipe(
      tap(session => this.storeSession(session))
    );
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
    localStorage.removeItem(this.storageKey);
    this.session.set(null);
  }

  private storeSession(session: AuthResponse): void {
    localStorage.setItem(this.storageKey, JSON.stringify(session));
    this.session.set(session);
  }

  private readSession(): AuthResponse | null {
    const value = localStorage.getItem(this.storageKey);
    if (!value) {
      return null;
    }
    try {
      return JSON.parse(value) as AuthResponse;
    } catch {
      localStorage.removeItem(this.storageKey);
      return null;
    }
  }
}
