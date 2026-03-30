import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { SearchResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './search.component.html',
  styleUrl: './search.component.css',
})
export class SearchComponent {
  query = signal('');
  results = signal<SearchResponse | null>(null);
  loading = signal(false);
  searched = signal(false);

  private searchSubject = new Subject<string>();

  constructor(private api: ApiService, private router: Router) {
    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q.trim()) {
          this.results.set(null);
          this.searched.set(false);
          this.loading.set(false);
          return of(null);
        }
        this.loading.set(true);
        return this.api.search({ query: q, max_results: 15 });
      })
    ).subscribe({
      next: res => {
        if (res) {
          this.results.set(res);
          this.searched.set(true);
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onInput(value: string) {
    this.query.set(value);
    this.searchSubject.next(value);
  }

  goToConcept(conceptName: string) {
    this.router.navigate(['/concepts'], { queryParams: { highlight: conceptName } });
  }
}
