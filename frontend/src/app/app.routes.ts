import { Routes } from '@angular/router';

import { ApplicationsPage } from './applications/applications-page';
import { AuthPage } from './auth/auth-page';
import { authGuard, guestGuard } from './core/auth.guard';
import { DashboardPage } from './dashboard/dashboard-page';
import { DiscoveryPage } from './discovery/discovery-page';
import { ProfilePage } from './profile/profile-page';
import { SettingsPage } from './settings/settings-page';
import { WorkspaceShell } from './workspace/workspace-shell';

export const routes: Routes = [
  {
    path: 'login',
    component: AuthPage,
    canActivate: [guestGuard]
  },
  {
    path: '',
    component: WorkspaceShell,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardPage },
      { path: 'discover', component: DiscoveryPage },
      { path: 'applications', component: ApplicationsPage },
      { path: 'profile', component: ProfilePage },
      { path: 'settings', component: SettingsPage },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
