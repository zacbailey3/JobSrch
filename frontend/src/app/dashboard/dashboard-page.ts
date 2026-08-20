import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { WorkspaceStore } from '../workspace/workspace.store';

@Component({
  selector: 'app-dashboard-page',
  imports: [FormsModule],
  templateUrl: './dashboard-page.html'
})
export class DashboardPage {
  constructor(readonly workspace: WorkspaceStore) {}
}
