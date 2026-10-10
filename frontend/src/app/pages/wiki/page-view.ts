import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { Page, Revision } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { DiffView } from '../../shared/diff-view';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { pageTypeLabel } from '../../shared/labels';
import { renderMarkdown } from '../../shared/markdown';
import { PageLinksDirective } from '../../shared/page-links.directive';

@Component({
  selector: 'app-page-view',
  standalone: true,
  imports: [RouterLink, MatButtonModule, DiffView, PageLinksDirective, DateTimePipe],
  styles: `
    .history { list-style: none; padding: 0; margin: 0; }
    .history li { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem;
      padding: 0.5rem 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
  `,
  template: `
    @if (page(); as current) {
      <article>
        <h1>{{ current.title }}</h1>
        <p><em>{{ current.description }}</em> · {{ typeLabel(current.type) }} · rewizja {{ current.revision }}</p>
        @if (current.type === 'Concept') {
          <a mat-stroked-button [routerLink]="['../../../study']" [queryParams]="{ pageId: current.pageId }">Ucz się tej strony</a>
        }
        <div class="markdown" appPageLinks [innerHTML]="html()"></div>
      </article>
      <section aria-labelledby="history-heading">
        <h2 id="history-heading">Historia</h2>
        <ul class="history">
          @for (revision of revisions(); track revision.revision; let i = $index) {
            <li>
              <span>
                Rewizja {{ revision.revision }} · {{ revision.createdAt | dateTime }}
                <a [routerLink]="['../../../runs', revision.ingestRunId]">przebieg</a>
              </span>
              <button mat-stroked-button type="button" [attr.aria-pressed]="selected() === i" (click)="selected.set(i)">
                Pokaż zmiany
              </button>
            </li>
          }
        </ul>
        @if (selectedDiff(); as diff) {
          <app-diff-view [before]="diff.before" [after]="diff.after" />
        }
      </section>
    }
  `,
})
export class PageView {
  private readonly wiki = inject(WikiApi);

  protected readonly typeLabel = pageTypeLabel;

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
      this.selected.set(null);
      this.wiki.page(this.kbId(), path).subscribe((page) => this.page.set(page));
      this.wiki.revisions(this.kbId(), path).subscribe((revisions) => this.revisions.set(revisions));
    });
  }
}
