import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { WorkspaceStore } from '../workspace/workspace.store';

@Component({
  selector: 'app-discovery-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './discovery-page.html'
})
export class DiscoveryPage {
  constructor(readonly workspace: WorkspaceStore) {}
}
