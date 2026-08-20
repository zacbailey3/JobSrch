import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { WorkspaceStore } from './workspace.store';

@Component({
  selector: 'app-workspace-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './workspace-shell.html'
})
export class WorkspaceShell implements OnInit {
  constructor(readonly workspace: WorkspaceStore) {}

  ngOnInit(): void {
    this.workspace.initialize();
  }
}
