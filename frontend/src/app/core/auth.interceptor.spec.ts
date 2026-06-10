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
      accessToken: 'stale-token',
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
    expect(request.request.headers.get('Authorization')).toBe('Bearer stale-token');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.session()).toBeNull();
    expect(localStorage.getItem('jobsrch-session')).toBeNull();
  });
});
