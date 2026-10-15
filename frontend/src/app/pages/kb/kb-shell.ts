import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { KnowledgeBase } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { ProgressService } from '../../core/services/progress.service';

@Component({
  selector: 'app-kb-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatTabsModule, MatButtonModule],
  providers: [ProgressService],
  template: `
    @if (kb(); as base) {
      <header class="row">
        <h1>{{ base.name }}</h1>
        <span class="spacer"></span>
        <a mat-stroked-button [href]="wiki.exportUrl(kbId())">Export bundle</a>
      </header>
      <nav mat-tab-nav-bar [tabPanel]="panel" aria-label="Knowledge base sections">
        @for (tab of tabs; track tab.path) {
          <a mat-tab-link [routerLink]="tab.path" routerLinkActive #active="routerLinkActive"
            [routerLinkActiveOptions]="{ exact: tab.path === '.' }" [active]="active.isActive">{{ tab.label }}</a>
        }
      </nav>
      <mat-tab-nav-panel #panel>
        <router-outlet />
      </mat-tab-nav-panel>
    }
  `,
  styles: `.spacer { flex: 1; } mat-tab-nav-panel { display: block; padding-top: 1rem; }`,
})
export class KbShell implements OnInit {
  readonly wiki = inject(WikiApi);
  private readonly progress = inject(ProgressService);
  private readonly router = inject(Router);

  readonly kbId = input.required<string>();
  readonly kb = signal<KnowledgeBase | null>(null);
  readonly tabs = [
    { path: '.', label: 'Documents' },
    { path: 'pages', label: 'Pages' },
    { path: 'graph', label: 'Graph' },
    { path: 'health', label: 'Health' },
    { path: 'study', label: 'Study' },
    { path: 'chat', label: 'Chat' },
  ];

  ngOnInit(): void {
    // the page shows nothing of a knowledge base that is not the reader's or does not exist; the toast says why
    this.wiki.knowledgeBase(this.kbId()).subscribe({
      next: (kb) => {
        this.kb.set(kb);
        this.progress.watch(this.kbId());
      },
      error: () => this.router.navigateByUrl('/dashboard'),
    });
  }
}
