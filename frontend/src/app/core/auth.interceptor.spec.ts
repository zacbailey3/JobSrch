import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('jobsrch-session', JSON.stringify({
      expiresIn: 3600,
      userId: 'user-1',
      email: 'stale@example.com',
      firstName: 'Stale',
      lastName: 'User'
    }));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('clears a stored session when the backend rejects it', () => {
    http.get('/api/dashboard').subscribe({ error: () => undefined });

    const request = httpTesting.expectOne('/api/dashboard');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.session()).toBeNull();
    expect(localStorage.getItem('jobsrch-session')).toBeNull();
  });

  it('does not attach an Authorization header to authentication requests', () => {
    http.post('/api/auth/register', {
      email: 'new@example.com',
      password: 'password123',
      firstName: 'New',
      lastName: 'User'
    }).subscribe();

    const request = httpTesting.expectOne('/api/auth/register');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({
      expiresIn: 3600,
      userId: 'user-2',
      email: 'new@example.com',
      firstName: 'New',
      lastName: 'User'
    });
  });

  it('does not attach a stored token to absolute authentication URLs', () => {
    http.post('http://localhost:8080/api/auth/password-reset/request', {
      email: 'new@example.com'
    }).subscribe();

    const request = httpTesting.expectOne(
      'http://localhost:8080/api/auth/password-reset/request');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({
      message: 'Reset instructions are available.',
      developmentResetToken: null,
      expiresAt: null
    });
  });
});
