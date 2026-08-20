import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { SettingsPage } from './settings-page';

describe('SettingsPage', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsPage);
  });

  afterEach(() => httpTesting.verify());

  it('loads current email and the secure account controls', () => {
    fixture.detectChanges();
    httpTesting.expectOne('/api/account').flush(accountResponse(futureTime()));
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('student@example.com');
    expect(text).toContain('Change password');
    expect(text).toContain('Send password-reset email');
    expect(text).toContain('Change email');
    expect(text).toContain('Type DELETE to confirm');
  });

  it('stores the refreshed session after password reauthentication', () => {
    fixture.detectChanges();
    httpTesting.expectOne('/api/account').flush(accountResponse(null));
    fixture.componentInstance.reauthenticationPassword = 'strong-password';
    fixture.componentInstance.reauthenticate();

    httpTesting.expectOne('/api/account/reauth/password').flush({
      expiresIn: 3600,
      userId: 'user-1',
      email: 'student@example.com',
      firstName: 'Student',
      lastName: 'Developer',
      authenticatedAt: '2026-08-20T22:00:00Z'
    });
    httpTesting.expectOne('/api/account').flush(accountResponse(futureTime()));

    expect(TestBed.inject(AuthService).session()?.email).toBe('student@example.com');
    expect(fixture.componentInstance.success()).toContain('unlocked for ten minutes');
  });
});

function accountResponse(recentAuthenticationExpiresAt: string | null) {
  return {
    email: 'student@example.com',
    emailVerified: false,
    emailVerifiedAt: null,
    authenticatedAt: recentAuthenticationExpiresAt ? '2026-08-20T22:00:00Z' : null,
    recentAuthenticationExpiresAt
  };
}

function futureTime(): string {
  return new Date(Date.now() + 10 * 60 * 1000).toISOString();
}
