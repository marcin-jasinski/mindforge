import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { KnowledgeBase } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';
import { ProgressService } from '../../core/services/progress.service';

@Component({
  selector: 'app-kb-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatTabsModule, MatButtonModule],
  providers: [ProgressService],
  template: `
    <header class="row">
      <h1>{{ kb()?.name }}</h1>
      <span class="spacer"></span>
      <a mat-stroked-button [href]="'/api/knowledge-bases/' + kbId() + '/export'">Eksportuj bundle</a>
    </header>
    <nav mat-tab-nav-bar [tabPanel]="panel" aria-label="Sekcje bazy wiedzy">
      @for (tab of tabs; track tab.path) {
        <a mat-tab-link [routerLink]="tab.path" routerLinkActive #active="routerLinkActive"
          [routerLinkActiveOptions]="{ exact: tab.path === '.' }" [active]="active.isActive">{{ tab.label }}</a>
      }
    </nav>
    <mat-tab-nav-panel #panel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: `.spacer { flex: 1; } mat-tab-nav-panel { display: block; padding-top: 1rem; }`,
})
export class KbShell implements OnInit {
  private readonly api = inject(ApiService);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly kb = signal<KnowledgeBase | null>(null);
  readonly tabs = [
    { path: '.', label: 'Dokumenty' },
    { path: 'pages', label: 'Strony' },
    { path: 'graph', label: 'Graf' },
    { path: 'health', label: 'Zdrowie' },
    { path: 'study', label: 'Nauka' },
    { path: 'chat', label: 'Czat' },
  ];

  ngOnInit(): void {
    this.api.get<KnowledgeBase>(`/knowledge-bases/${this.kbId()}`).subscribe((kb) => this.kb.set(kb));
    this.progress.watch(this.kbId());
  }
}
