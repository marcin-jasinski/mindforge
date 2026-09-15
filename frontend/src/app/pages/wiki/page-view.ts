import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';

import { Page, Revision } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';
import { DiffView } from '../../shared/diff-view';
import { renderMarkdown } from '../../shared/markdown';
import { PageLinksDirective } from '../../shared/page-links.directive';

@Component({
  selector: 'app-page-view',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatListModule, DiffView, PageLinksDirective],
  template: `
    @if (page(); as current) {
      <article>
        <h1>{{ current.title }}</h1>
        <p><em>{{ current.description }}</em> · {{ current.type }} · rewizja {{ current.revision }}</p>
        @if (current.type === 'Concept') {
          <a mat-stroked-button [routerLink]="['../../../study']" [queryParams]="{ pageId: current.pageId }">Ucz się tej strony</a>
        }
        <div class="markdown" appPageLinks [innerHTML]="html()"></div>
      </article>
      <section aria-labelledby="history-heading">
        <h2 id="history-heading">Historia</h2>
        <mat-list>
          @for (revision of revisions(); track revision.revision; let i = $index) {
            <mat-list-item>
              <span matListItemTitle>
                Rewizja {{ revision.revision }} · {{ revision.createdAt }}
                <a [routerLink]="['../../../runs', revision.ingestRunId]">przebieg</a>
              </span>
              <button mat-button type="button" matListItemMeta (click)="selected.set(i)">Pokaż zmiany</button>
            </mat-list-item>
          }
        </mat-list>
        @if (selectedDiff(); as diff) {
          <app-diff-view [before]="diff.before" [after]="diff.after" />
        }
      </section>
    }
  `,
})
export class PageView {
  private readonly api = inject(ApiService);

  readonly kbId = input.required<string>();
  readonly directory = input.required<string>();
  readonly name = input.required<string>();
  readonly page = signal<Page | null>(null);
  readonly revisions = signal<Revision[]>([]);
  readonly selected = signal<number | null>(null);
  readonly html = computed(() => {
    const page = this.page();
    return page ? renderMarkdown(page.markdown, this.kbId()) : '';
  });
  readonly selectedDiff = computed(() => {
    const index = this.selected();
    const revisions = this.revisions();
    if (index === null || !revisions[index]) {
      return null;
    }
    return { before: revisions[index - 1]?.markdownBody ?? null, after: revisions[index].markdownBody ?? null };
  });

  constructor() {
    effect(() => {
      const path = `${this.directory()}/${this.name()}`;
      const base = `/knowledge-bases/${this.kbId()}/pages/${path}`;
      this.selected.set(null);
      this.api.get<Page>(base).subscribe((page) => this.page.set(page));
      this.api.get<Revision[]>(`${base}/revisions`).subscribe((revisions) => this.revisions.set(revisions));
    });
  }
}
