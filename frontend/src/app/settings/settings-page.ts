import { Component } from '@angular/core';

@Component({
  selector: 'app-settings-page',
  template: `
    <section class="panel settings-placeholder">
      <p class="eyebrow">Account settings</p>
      <h2>Your account preferences will live here</h2>
      <p class="muted">
        Secure password, email, linked-login, and deletion controls are the next
        verified sprint. Existing account controls remain unchanged until their
        replacement is complete.
      </p>
    </section>
  `
})
export class SettingsPage {}
