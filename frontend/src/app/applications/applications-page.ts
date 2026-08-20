import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { WorkspaceStore } from '../workspace/workspace.store';

@Component({
  selector: 'app-applications-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './applications-page.html'
})
export class ApplicationsPage {
  constructor(readonly workspace: WorkspaceStore) {}
}
