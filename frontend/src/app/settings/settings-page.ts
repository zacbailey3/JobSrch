import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AccountPreferences, ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-settings-page',
  imports: [FormsModule],
  templateUrl: './settings-page.html'
})
export class SettingsPage implements OnInit {
  readonly account = signal<AccountPreferences | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  reauthenticationPassword = '';
  password = { value: '', confirmation: '' };
  newEmail = '';
  emailChangeToken = '';
  deletionConfirmation = '';

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const fragment = new URLSearchParams(this.route.snapshot.fragment ?? '');
    const emailChangeToken = fragment.get('emailChangeToken');
    if (emailChangeToken) {
      this.emailChangeToken = emailChangeToken;
      void this.router.navigate([], {
        relativeTo: this.route,
        fragment: undefined,
        replaceUrl: true
      });
    }
    this.loadAccount();
  }

  isRecentlyAuthenticated(): boolean {
    const expiresAt = this.account()?.recentAuthenticationExpiresAt;
    return Boolean(expiresAt && new Date(expiresAt).getTime() > Date.now());
  }

  reauthenticate(): void {
    if (!this.reauthenticationPassword) {
      return;
    }
    this.beginRequest();
    this.api.reauthenticatePassword(this.reauthenticationPassword).subscribe({
      next: session => {
        this.auth.acceptSession(session);
        this.reauthenticationPassword = '';
        this.busy.set(false);
        this.success.set('Password confirmed. Sensitive controls are unlocked for ten minutes.');
        this.loadAccount(false);
      },
      error: error => this.handleError(error)
    });
  }

  changePassword(): void {
    if (this.password.value !== this.password.confirmation) {
      this.error.set('New passwords must match.');
      return;
    }
    this.beginRequest();
    this.api.changePassword(this.password.value).subscribe({
      next: () => this.finishSensitiveChange('password-changed'),
      error: error => this.handleError(error)
    });
  }

  sendPasswordReset(): void {
    const email = this.account()?.email;
    if (!email) {
      return;
    }
    this.beginRequest();
    this.auth.requestPasswordReset(email).subscribe({
      next: response => {
        this.busy.set(false);
        this.success.set(response.message);
      },
      error: error => this.handleError(error)
    });
  }

  requestEmailChange(): void {
    this.beginRequest();
    this.api.requestEmailChange(this.newEmail).subscribe({
      next: response => {
        this.busy.set(false);
        this.success.set(response.message);
        if (response.developmentToken) {
          this.emailChangeToken = response.developmentToken;
        }
      },
      error: error => this.handleError(error)
    });
  }

  confirmEmailChange(): void {
    this.beginRequest();
    this.api.confirmEmailChange(this.emailChangeToken).subscribe({
      next: () => this.finishSensitiveChange('email-changed'),
      error: error => this.handleError(error)
    });
  }

  deleteAccount(): void {
    if (this.deletionConfirmation !== 'DELETE'
        || !window.confirm('Permanently delete your JobSrch account and private files?')) {
      return;
    }
    this.beginRequest();
    this.api.deleteAccount(this.deletionConfirmation).subscribe({
      next: () => this.finishSensitiveChange('account-deleted'),
      error: error => this.handleError(error)
    });
  }

  private loadAccount(showLoading = true): void {
    if (showLoading) {
      this.loading.set(true);
    }
    this.api.getAccountPreferences().subscribe({
      next: account => {
        this.account.set(account);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.handleError(error);
      }
    });
  }

  private finishSensitiveChange(notice: string): void {
    this.auth.clearSession();
    this.busy.set(false);
    void this.router.navigate(['/login'], { queryParams: { notice } });
  }

  private beginRequest(): void {
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
  }

  private handleError(error: HttpErrorResponse): void {
    this.busy.set(false);
    this.error.set(error.error?.detail ?? 'Something went wrong. Please try again.');
  }
}
