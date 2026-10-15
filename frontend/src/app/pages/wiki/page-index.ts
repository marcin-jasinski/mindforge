import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';

import { IndexEntry, IndexView } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { pageRoute } from '../../shared/diff';

@Component({
  selector: 'app-page-index',
  standalone: true,
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field class="search">
      <mat-label>Search pages</mat-label>
      <input matInput type="search" [ngModel]="query()" (ngModelChange)="search($event)" />
    </mat-form-field>
    @if (query().trim().length >= 2) {
      <h2>Results</h2>
      <ul>
        @for (page of results(); track page.path) {
          <li><a [routerLink]="link(page)">{{ page.title }}</a> — {{ page.description }}</li>
        } @empty {
          <li>No results.</li>
        }
      </ul>
    }
    @for (group of groups(); track group.label) {
      <h2>{{ group.label }}</h2>
      <ul>
        @for (page of group.pages; track page.path) {
          <li><a [routerLink]="link(page)">{{ page.title }}</a> — {{ page.description }}</li>
        } @empty {
          <li>No pages.</li>
        }
      </ul>
    }
  `,
  styles: `.search { width: 100%; }`,
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
      { label: 'Concepts', pages: pages.filter((page) => page.type === 'Concept') },
      { label: 'Sources', pages: pages.filter((page) => page.type === 'Source Summary') },
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
