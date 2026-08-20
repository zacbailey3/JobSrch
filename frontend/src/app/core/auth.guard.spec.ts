import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree
} from '@angular/router';
import { Observable } from 'rxjs';

import { authGuard, guestGuard } from './auth.guard';
import { AuthResponse } from './auth.service';

describe('authentication route guards', () => {
  let httpTesting: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpTesting.verify());

  it('restores a valid cookie session before activating a protected route', () => {
    let decision: boolean | UrlTree | undefined;
    const result = TestBed.runInInjectionContext(() => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/profile' } as RouterStateSnapshot
    )) as Observable<boolean | UrlTree>;
    result.subscribe(value => decision = value);

    httpTesting.expectOne('/api/auth/session').flush(session());
    expect(decision).toBe(true);
  });

  it('sends an anonymous visitor to login with a safe return URL', () => {
    let decision: boolean | UrlTree | undefined;
    const result = TestBed.runInInjectionContext(() => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/applications' } as RouterStateSnapshot
    )) as Observable<boolean | UrlTree>;
    result.subscribe(value => decision = value);

    httpTesting.expectOne('/api/auth/session').flush({}, {
      status: 401,
      statusText: 'Unauthorized'
    });

    expect(router.serializeUrl(decision as UrlTree))
      .toBe('/login?returnUrl=%2Fapplications');
  });

  it('preserves reset links that use the legacy root URL', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/?resetToken=one-time-token' } as RouterStateSnapshot
    ));

    expect(router.serializeUrl(result as UrlTree))
      .toBe('/login?resetToken=one-time-token');
    httpTesting.expectNone('/api/auth/session');
  });

  it('allows a password-reset link even when a cookie session exists', () => {
    const route = {
      queryParamMap: { has: (name: string) => name === 'resetToken' }
    } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => guestGuard(
      route,
      { url: '/login?resetToken=one-time-token' } as RouterStateSnapshot
    ));

    expect(result).toBe(true);
    httpTesting.expectNone('/api/auth/session');
  });

  it('keeps an authenticated user out of the guest login route', () => {
    let decision: boolean | UrlTree | undefined;
    const route = {
      queryParamMap: { has: () => false }
    } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => guestGuard(
      route,
      { url: '/login' } as RouterStateSnapshot
    )) as Observable<boolean | UrlTree>;
    result.subscribe(value => decision = value);

    httpTesting.expectOne('/api/auth/session').flush(session());
    expect(router.serializeUrl(decision as UrlTree)).toBe('/dashboard');
  });
});

function session(): AuthResponse {
  return {
    expiresIn: 3600,
    userId: 'user-1',
    email: 'student@example.com',
    firstName: 'Student',
    lastName: 'Developer',
    authenticatedAt: '2026-08-20T20:00:00Z'
  };
}
