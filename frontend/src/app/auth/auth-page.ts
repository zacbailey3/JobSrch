import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-auth-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './auth-page.html'
})
export class AuthPage implements OnInit {
  readonly authMode = signal<'login' | 'register' | 'forgot' | 'reset'>('login');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  credentials = {
    email: '',
    password: '',
    firstName: '',
    lastName: ''
  };

  passwordReset = {
    email: '',
    token: '',
    password: '',
    confirmPassword: ''
  };

  constructor(
    readonly auth: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const resetToken = this.route.snapshot.queryParamMap.get('resetToken');
    const notice = this.route.snapshot.queryParamMap.get('notice');
    if (resetToken) {
      this.passwordReset.token = resetToken;
      this.authMode.set('reset');
    }
    const notices: Record<string, string> = {
      'password-changed': 'Password changed. Sign in with your new password.',
      'email-changed': 'Email changed and verified. Sign in with your new email.',
      'account-deleted': 'Your account and private JobSrch data were deleted.'
    };
    if (notice && notices[notice]) {
      this.success.set(notices[notice]);
    }
    if (resetToken || notice) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { resetToken: null, notice: null },
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
    }
  }

  submitAuth(): void {
    this.beginRequest();
    const request = this.authMode() === 'login'
      ? this.auth.login(this.credentials.email, this.credentials.password)
      : this.auth.register(this.credentials);

    request.subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(this.safeReturnUrl(returnUrl));
      },
      error: error => this.handleError(error)
    });
  }

  toggleAuthMode(): void {
    this.error.set('');
    this.success.set('');
    this.authMode.update(mode => mode === 'login' ? 'register' : 'login');
  }

  showForgotPassword(): void {
    this.error.set('');
    this.success.set('');
    this.passwordReset.email = this.credentials.email;
    this.authMode.set('forgot');
  }

  showLogin(): void {
    this.error.set('');
    this.success.set('');
    this.authMode.set('login');
  }

  requestPasswordReset(): void {
    this.beginRequest();
    this.auth.requestPasswordReset(this.passwordReset.email).subscribe({
      next: response => {
        this.loading.set(false);
        this.success.set(response.message);
        if (response.developmentResetToken) {
          this.passwordReset.token = response.developmentResetToken;
          this.authMode.set('reset');
        }
      },
      error: error => this.handleError(error)
    });
  }

  submitPasswordReset(): void {
    if (this.passwordReset.password !== this.passwordReset.confirmPassword) {
      this.error.set('Passwords must match.');
      return;
    }
    this.beginRequest();
    this.auth.resetPassword(this.passwordReset.token, this.passwordReset.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.credentials.email = this.passwordReset.email;
        this.credentials.password = '';
        this.passwordReset = {
          email: '',
          token: '',
          password: '',
          confirmPassword: ''
        };
        this.authMode.set('login');
        this.success.set('Password updated. Sign in with your new password.');
      },
      error: error => this.handleError(error)
    });
  }

  private safeReturnUrl(value: string | null): string {
    return value?.startsWith('/') && !value.startsWith('//') ? value : '/dashboard';
  }

  private beginRequest(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
  }

  private handleError(error: HttpErrorResponse): void {
    this.loading.set(false);
    this.error.set(error.error?.detail ?? 'Something went wrong. Please try again.');
  }
}
