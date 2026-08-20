import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthResponse, AuthService } from './auth.service';

describe('AuthService session restoration', () => {
  let auth: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    auth = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('hydrates metadata from the protected cookie-backed session endpoint', () => {
    const session: AuthResponse = {
      expiresIn: 3600,
      userId: 'user-1',
      email: 'student@example.com',
      firstName: 'Student',
      lastName: 'Developer',
      authenticatedAt: '2026-08-20T20:00:00Z'
    };
    let restored: AuthResponse | null | undefined;

    auth.restoreSession().subscribe(value => restored = value);
    httpTesting.expectOne('/api/auth/session').flush(session);

    expect(restored).toEqual(session);
    expect(auth.session()).toEqual(session);
    expect(auth.sessionChecked()).toBe(true);
  });

  it('performs only one restoration request after the session has been checked', () => {
    auth.restoreSession().subscribe();
    httpTesting.expectOne('/api/auth/session').flush({}, {
      status: 401,
      statusText: 'Unauthorized'
    });

    auth.restoreSession().subscribe(value => expect(value).toBeNull());
    httpTesting.expectNone('/api/auth/session');
    expect(auth.sessionChecked()).toBe(true);
  });
});
