import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { WorkspaceStore } from '../workspace/workspace.store';

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './profile-page.html'
})
export class ProfilePage {
  constructor(readonly workspace: WorkspaceStore) {}
}
