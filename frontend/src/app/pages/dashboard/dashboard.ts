import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { KnowledgeBase } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, Icon],
  templateUrl: './dashboard.html',
  styles: `
    .head { margin-bottom: 1.5rem; align-items: flex-end; }
    .intro { gap: 0.4rem; }
    .create { margin-bottom: 1.5rem; }
    .fields { display: grid; gap: 0.75rem; margin-top: 1rem; align-items: center;
      @media (min-width: 48rem) { grid-template-columns: 1fr 2fr auto; } }
    .kb { position: relative; display: flex; flex-direction: column; gap: 0.9rem; transition: border-color 160ms, transform 160ms; }
    .kb:hover { border-color: rgb(165 148 255 / 0.45); transform: translateY(-2px); }
    .top { flex-wrap: nowrap; gap: 0.75rem; }
    .monogram { font-weight: 700; font-size: 1.1rem; background: var(--mf-gradient); color: #0a0b12; }
    .stretched { color: var(--mf-text); }
    .stretched::after { content: ''; position: absolute; inset: 0; border-radius: inherit; }
    .description { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 2.6em; }
    .actions { position: relative; z-index: 1; margin: auto -0.5rem -0.5rem; }
    .tile-skeleton { height: 12rem; border-radius: var(--mf-radius); }
  `,
})
export class Dashboard implements OnInit {
  private readonly wiki = inject(WikiApi);
  private readonly router = inject(Router);

  readonly knowledgeBases = signal<KnowledgeBase[]>([]);
  readonly loaded = signal(false);
  readonly creating = signal(false);
  readonly name = signal('');
  readonly description = signal('');

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    // a new knowledge base is empty, so the next step is always an upload: open it at once
    this.wiki.createKnowledgeBase(this.name(), this.description()).subscribe((kb) => {
      this.router.navigate(['/kb', kb.kbId]);
    });
  }

  private load(): void {
    this.wiki.knowledgeBases().subscribe((bases) => {
      this.knowledgeBases.set(bases);
      this.loaded.set(true);
    });
  }
}
