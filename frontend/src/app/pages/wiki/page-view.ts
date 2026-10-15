import { Component, Injector, afterNextRender, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { Page, Revision } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { DiffView } from '../../shared/diff-view';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { Icon } from '../../shared/icon';
import { pageTypeLabel } from '../../shared/labels';
import { renderMarkdown } from '../../shared/markdown';
import { PageLinksDirective } from '../../shared/page-links.directive';

@Component({
  selector: 'app-page-view',
  standalone: true,
  imports: [RouterLink, MatButtonModule, DiffView, PageLinksDirective, DateTimePipe, Icon],
  styles: `
    .crumbs { display: flex; align-items: center; gap: 0.25rem; margin-bottom: 1.25rem; font-size: 0.85rem; color: var(--mf-muted); }
    .crumbs a { color: var(--mf-muted); }
    .layout { display: grid; gap: 2rem; align-items: start; }
    .layout > * { min-width: 0; }
    @media (min-width: 64rem) { .layout { grid-template-columns: minmax(0, 1fr) 19rem; } }
    article { max-width: var(--mf-reading); }
    header { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 2rem; }
    header h1 { font-size: clamp(1.9rem, 1.4rem + 1.8vw, 2.6rem); }
    .lead { font-size: 1.1rem; color: var(--mf-muted); }
    aside { display: flex; flex-direction: column; gap: 1rem; }
    @media (min-width: 64rem) { aside { position: sticky; top: 7.5rem; } }
    .meta { margin: 0; display: grid; grid-template-columns: auto 1fr; gap: 0.4rem 1rem; font-size: 0.85rem; }
    .meta dt { color: var(--mf-muted); }
    .meta dd { margin: 0; }
    .actions { display: grid; gap: 0.5rem; }
    .history li { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; padding: 0.6rem 0; font-size: 0.85rem; }
    .rev { display: flex; flex-direction: column; gap: 0.1rem; }
    .history button[aria-pressed='true'] { background: var(--mf-accent-soft); }
    .changes { margin-top: 2.5rem; }
  `,
  template: `
    @if (page(); as current) {
      <nav class="crumbs" aria-label="Breadcrumb">
        <a routerLink="../../../pages">Pages</a><app-icon name="chevronRight" size="1rem" /><span>{{ current.title }}</span>
      </nav>
      <div class="layout">
        <article>
          <header>
            <div class="row">
              <span [class]="current.type === 'Concept' ? 'chip accent' : 'chip cyan'">{{ typeLabel(current.type) }}</span>
              <span class="muted small">{{ readingMinutes() }} min read</span>
            </div>
            <h1>{{ current.title }}</h1>
            <p class="lead">{{ current.description }}</p>
          </header>
          <div class="markdown" appPageLinks [innerHTML]="html()"></div>

          @if (selectedDiff(); as diff) {
            <section class="changes stack" aria-labelledby="changes-heading">
              <div class="section-head">
                <h2 id="changes-heading">Changes in revision {{ diff.revision }}</h2>
                <span class="spacer"></span>
                <button mat-button type="button" (click)="selected.set(null)"><app-icon name="close" /> Close</button>
              </div>
              <app-diff-view [before]="diff.before" [after]="diff.after" />
            </section>
          }
        </article>

        <aside>
          <section class="card actions" aria-label="Learn this page">
            <span class="eyebrow">Learn</span>
            @if (current.type === 'Concept') {
              <a mat-flat-button [routerLink]="['../../../study']" [queryParams]="{ pageId: current.pageId }">
                <app-icon name="school" /> Study this page
              </a>
            }
            <a mat-stroked-button [routerLink]="['../../../chat']" [queryParams]="{ q: 'Explain “' + current.title + '” in simple terms.' }">
              <app-icon name="chat" /> Ask about this page
            </a>
          </section>
          <section class="card" aria-label="Page details">
            <dl class="meta">
              <dt>Revision</dt><dd>{{ current.revision }}</dd>
              <dt>Updated</dt><dd>{{ current.updatedAt | dateTime }}</dd>
              <dt>Path</dt><dd class="mono">{{ current.path }}</dd>
            </dl>
          </section>
          <section class="card" aria-labelledby="history-heading">
            <div class="section-head">
              <app-icon name="history" />
              <h2 id="history-heading">History</h2>
            </div>
            <ul class="list history">
              @for (revision of revisions(); track revision.revision; let i = $index) {
                <li>
                  <span class="rev">
                    <strong>Revision {{ revision.revision }}</strong>
                    <span class="muted">{{ revision.createdAt | dateTime }}</span>
                    <a [routerLink]="['../../../runs', revision.ingestRunId]">View run</a>
                  </span>
                  <button mat-button type="button" [attr.aria-pressed]="selected() === i" (click)="toggleChanges(i)">
                    Changes
                  </button>
                </li>
              }
            </ul>
          </section>
        </aside>
      </div>
    } @else {
      <div class="stack" aria-busy="true" aria-label="Loading">
        <div class="skeleton" style="height: 3rem; width: 60%"></div>
        <div class="skeleton" style="height: 1.25rem; width: 40%"></div>
        <div class="skeleton" style="height: 20rem"></div>
      </div>
    }
  `,
})
export class PageView {
  private readonly wiki = inject(WikiApi);
  private readonly injector = inject(Injector);

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
  readonly readingMinutes = computed(() => Math.max(1, Math.round((this.page()?.markdown.split(/\s+/).length ?? 0) / 200)));
  readonly selectedDiff = computed(() => {
    const index = this.selected();
    const revisions = this.revisions();
    if (index === null || !revisions[index]) {
      return null;
    }
    return {
      revision: revisions[index].revision,
      before: revisions[index - 1]?.markdownBody ?? null,
      after: revisions[index].markdownBody ?? null,
    };
  });

  /** The changes open below the article, far from the history list, so they are brought into view. */
  toggleChanges(index: number): void {
    this.selected.set(this.selected() === index ? null : index);
    if (this.selected() !== null) {
      afterNextRender(() => document.getElementById('changes-heading')?.scrollIntoView?.({ behavior: 'smooth' }),
        { injector: this.injector });
    }
  }

  constructor() {
    effect(() => {
      const path = `${this.directory()}/${this.name()}`;
      this.selected.set(null);
      this.page.set(null);
      this.wiki.page(this.kbId(), path).subscribe((page) => this.page.set(page));
      this.wiki.revisions(this.kbId(), path).subscribe((revisions) => this.revisions.set(revisions));
    });
  }
}
