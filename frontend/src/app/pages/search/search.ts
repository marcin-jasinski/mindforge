import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { SearchService } from '../../core/services/search.service';
import type { SearchResultItem } from '../../core/models/api.models';

@Component({
  selector: 'app-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatChipsModule, MatDividerModule,
  ],
  templateUrl: './search.html',
  styleUrl: './search.scss',
})
export class SearchComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly searchService = inject(SearchService);

  readonly kbId = signal('');
  readonly query = signal('');
  readonly results = signal<SearchResultItem[]>([]);
  readonly searched = signal(false);
  readonly loading = signal(false);

  ngOnInit() {
    this.kbId.set(this.route.snapshot.paramMap.get('kbId') ?? '');
  }

  search() {
    if (!this.query().trim()) return;
    this.loading.set(true);
    this.searchService.search(this.kbId(), { query: this.query(), top_k: 8 }).subscribe({
      next: res => { this.results.set(res.results); this.loading.set(false); this.searched.set(true); },
      error: () => this.loading.set(false),
    });
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter') this.search();
  }

  scorePercent(score: number): number {
    return Math.round(score * 100);
  }
}
