import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { KnowledgeBase } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { ProgressService } from '../../core/services/progress.service';
import { Icon, IconName } from '../../shared/icon';
import { runKindLabel } from '../../shared/labels';

const ACTIVE = new Set(['QUEUED', 'RUNNING', 'WRITTEN']);

@Component({
  selector: 'app-kb-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, Icon],
  providers: [ProgressService],
  template: `
    <a class="crumb" routerLink="/dashboard"><app-icon name="arrowBack" size="1rem" /> Knowledge bases</a>
    @if (kb(); as base) {
      <header class="head">
        <div class="stack title">
          <h1>{{ base.name }}</h1>
          @if (base.description) {
            <p class="muted">{{ base.description }}</p>
          }
          <div class="row">
            <span class="chip">{{ base.documentCount }} {{ base.documentCount === 1 ? 'document' : 'documents' }}</span>
            <span class="chip">{{ base.pageCount }} {{ base.pageCount === 1 ? 'page' : 'pages' }}</span>
            @if (activeRun(); as run) {
              <span class="chip status-running" role="status">{{ run }} in progress</span>
            }
          </div>
        </div>
        <a mat-stroked-button [href]="wiki.exportUrl(kbId())" title="Download the wiki as an OKF bundle">
          <app-icon name="download" /> Export
        </a>
      </header>
      <nav class="tabs" aria-label="Knowledge base sections">
        @for (tab of tabs; track tab.path) {
          <a [routerLink]="tab.path" routerLinkActive="active" ariaCurrentWhenActive="page"
            [routerLinkActiveOptions]="{ exact: tab.path === '.' }">
            <app-icon [name]="tab.icon" size="1.1rem" /> {{ tab.label }}
          </a>
        }
      </nav>
      <div class="panel">
        <router-outlet />
      </div>
    } @else {
      <div class="stack" aria-busy="true" aria-label="Loading">
        <div class="skeleton" style="height: 2.5rem; width: 40%"></div>
        <div class="skeleton" style="height: 3rem"></div>
        <div class="skeleton" style="height: 16rem"></div>
      </div>
    }
  `,
  styles: `
    .crumb { display: inline-flex; align-items: center; gap: 0.35rem; color: var(--mf-muted); font-size: 0.85rem; margin-bottom: 1rem; }
    .crumb:hover { color: var(--mf-text); text-decoration: none; }
    .head { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-start; justify-content: space-between; margin-bottom: 1.5rem; }
    .title { gap: 0.6rem; }
    .tabs { position: sticky; top: 3.3rem; z-index: 5; display: flex; gap: 0.25rem; padding: 0.3rem; overflow-x: auto;
      scrollbar-width: none; border-radius: 0.9rem; border: 1px solid var(--mf-border);
      background: rgb(18 20 31 / 0.85); backdrop-filter: blur(12px); }
    .tabs a { display: inline-flex; align-items: center; gap: 0.45rem; padding: 0.55rem 0.95rem; border-radius: 0.65rem;
      color: var(--mf-muted); font-size: 0.9rem; font-weight: 550; white-space: nowrap; transition: background-color 150ms, color 150ms; }
    .tabs a:hover { text-decoration: none; color: var(--mf-text); background: rgb(255 255 255 / 0.05); }
    .tabs a.active { color: var(--mf-text); background: var(--mf-accent-soft); box-shadow: inset 0 0 0 1px rgb(165 148 255 / 0.35); }
    .tabs a.active app-icon { color: var(--mf-accent); }
    .panel { padding-top: 1.75rem; }
  `,
})
export class KbShell implements OnInit {
  readonly wiki = inject(WikiApi);
  private readonly progress = inject(ProgressService);
  private readonly router = inject(Router);

  readonly kbId = input.required<string>();
  readonly kb = signal<KnowledgeBase | null>(null);
  /** The run the knowledge base is busy with, shown on every tab so a learner knows the wiki is changing. */
  readonly activeRun = computed(() => {
    const run = this.progress.runs().find((candidate) => ACTIVE.has(candidate.status));
    return run ? runKindLabel(run.kind) : null;
  });
  readonly tabs: { path: string; label: string; icon: IconName }[] = [
    { path: '.', label: 'Documents', icon: 'folder' },
    { path: 'pages', label: 'Pages', icon: 'article' },
    { path: 'study', label: 'Study', icon: 'school' },
    { path: 'chat', label: 'Chat', icon: 'chat' },
    { path: 'graph', label: 'Graph', icon: 'graph' },
    { path: 'health', label: 'Health', icon: 'health' },
  ];

  constructor() {
    // a finished run changes the document and page counts in the header
    effect(() => {
      this.progress.runs();
      if (untracked(this.kb)) {
        this.wiki.knowledgeBase(this.kbId()).subscribe((kb) => this.kb.set(kb));
      }
    });
  }

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
