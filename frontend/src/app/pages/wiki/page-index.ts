import { NgTemplateOutlet } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';

import { IndexEntry, IndexView } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { pageRoute } from '../../shared/diff';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-page-index',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink, Icon],
  template: `
    <label class="search">
      <app-icon name="search" />
      <span class="visually-hidden">Search pages</span>
      <input type="search" placeholder="Search pages…" [value]="query()" (input)="search($any($event.target).value)" />
    </label>

    @if (query().trim().length >= 2) {
      <section class="group" aria-labelledby="results-heading">
        <div class="section-head"><h2 id="results-heading">Results</h2><span class="chip">{{ results().length }}</span></div>
        <div class="grid">
          @for (page of results(); track page.path) {
            <ng-container *ngTemplateOutlet="tile; context: { $implicit: page }" />
          } @empty {
            <p class="muted">No page matches “{{ query().trim() }}”.</p>
          }
        </div>
      </section>
    } @else if (index() === null) {
      <div class="grid">
        @for (i of [1, 2, 3, 4, 5, 6]; track i) { <div class="skeleton" style="height: 7rem"></div> }
      </div>
    } @else if (!index()!.pages.length) {
      <div class="empty">
        <span class="icon-tile"><app-icon name="article" /></span>
        <strong>The wiki has no pages yet</strong>
        <span>Upload a document and MindForge writes the first pages.</span>
        <a routerLink="..">Go to documents</a>
      </div>
    } @else {
      @for (group of groups(); track group.label) {
        @if (group.pages.length) {
          <section class="group" [attr.aria-label]="group.label">
            <div class="section-head">
              <span class="icon-tile" [class.cyan]="group.label === 'Sources'"><app-icon [name]="group.icon" /></span>
              <h2>{{ group.label }}</h2>
              <span class="chip">{{ group.pages.length }}</span>
              <span class="muted small">{{ group.hint }}</span>
            </div>
            <div class="grid">
              @for (page of group.pages; track page.path) {
                <ng-container *ngTemplateOutlet="tile; context: { $implicit: page }" />
              }
            </div>
          </section>
        }
      }
    }

    <ng-template #tile let-page>
      <a class="card card-link page" [routerLink]="link(page)">
        <span [class]="page.type === 'Concept' ? 'chip accent' : 'chip cyan'">{{ page.type === 'Concept' ? 'Concept' : 'Source' }}</span>
        <strong>{{ page.title }}</strong>
        <span class="muted small">{{ page.description }}</span>
      </a>
    </ng-template>
  `,
  styles: `
    .search { display: flex; align-items: center; gap: 0.6rem; padding: 0 1rem; margin-bottom: 2rem; height: 3rem;
      border-radius: 0.9rem; border: 1px solid var(--mf-border-strong); background: var(--mf-surface); color: var(--mf-muted); }
    .search:focus-within { border-color: var(--mf-accent); box-shadow: 0 0 0 3px var(--mf-accent-soft); }
    .search input { flex: 1; min-width: 0; border: 0; outline: 0; background: none; color: var(--mf-text); font: inherit; font-size: 1rem; }
    .group + .group { margin-top: 2.25rem; }
    .page { display: flex; flex-direction: column; align-items: flex-start; gap: 0.5rem; padding: 1.1rem; }
    .page strong { font-size: 1.02rem; }
  `,
})
export class PageIndex implements OnInit {
  private readonly wiki = inject(WikiApi);
  private readonly queries = new Subject<string>();

  readonly kbId = input.required<string>();
  readonly index = signal<IndexView | null>(null);
  readonly query = signal('');
  readonly results = signal<IndexEntry[]>([]);
  readonly groups = computed(() => {
    const pages = this.index()?.pages ?? [];
    return [
      { label: 'Concepts', icon: 'lightbulb' as const, hint: 'Ideas the wiki explains and you can study',
        pages: pages.filter((page) => page.type === 'Concept') },
      { label: 'Sources', icon: 'document' as const, hint: 'Summaries of the documents you uploaded',
        pages: pages.filter((page) => page.type === 'Source Summary') },
    ];
  });

  constructor() {
    this.queries
      .pipe(
        debounceTime(250),
        switchMap((q) => this.wiki.search(this.kbId(), q)),
      )
      .subscribe((results) => this.results.set(results));
  }

  ngOnInit(): void {
    this.wiki.index(this.kbId()).subscribe((index) => this.index.set(index));
  }

  search(query: string): void {
    this.query.set(query);
    if (query.trim().length >= 2) {
      this.queries.next(query.trim());
    }
  }

  link(page: IndexEntry): string[] {
    return ['..', ...pageRoute(page.path)];
  }
}
