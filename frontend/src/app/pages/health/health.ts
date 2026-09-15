import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ApiError, Health } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';
import { ProgressService } from '../../core/services/progress.service';

@Component({
  selector: 'app-health',
  standalone: true,
  imports: [MatButtonModule],
  template: `
    @if (health(); as h) {
      <p>
        Indeks: ≈ {{ h.indexTokens }} / {{ h.indexCeilingTokens }} tokenów
        @if (h.prefilterDue) {
          <strong> — potrzebny prefiltr</strong>
        }
      </p>
      <button mat-flat-button type="button" (click)="runReview()">Uruchom pełny przegląd</button>
      @if (message(); as text) {
        <p role="status">{{ text }}</p>
      }
      <h2>Niedziałające linki</h2>
      <ul>
        @for (link of h.danglingLinks; track $index) {
          <li>{{ link.sourcePath }} → {{ link.targetPath }}</li>
        } @empty { <li>Brak.</li> }
      </ul>
      <h2>Linki do złego katalogu</h2>
      <ul>
        @for (link of h.wrongDirectoryLinks; track $index) {
          <li>{{ link.sourcePath }} → {{ link.targetPath }} (chodziło o {{ link.livePath }}?)</li>
        } @empty { <li>Brak.</li> }
      </ul>
      <h2>Pojęcia bez linków przychodzących</h2>
      <ul>
        @for (path of h.orphanConcepts; track path) { <li>{{ path }}</li> } @empty { <li>Brak.</li> }
      </ul>
      <h2>Powtórzone tytuły</h2>
      <ul>
        @for (dup of h.duplicateConceptTitles; track dup.title) {
          <li>{{ dup.title }}: {{ dup.paths.join(', ') }}</li>
        } @empty { <li>Brak.</li> }
      </ul>
      <h2>Nieaktualne noty o poprawkach</h2>
      <ul>
        @for (row of h.danglingSupersessions; track row.supersessionId) {
          <li>{{ row.supersededPath }}#{{ row.sectionAnchor }} ← {{ row.supersedingPath ?? '(usunięta strona)' }}</li>
        } @empty { <li>Brak.</li> }
      </ul>
      <h2>Ostatni pełny przegląd</h2>
      @if (h.latestReview; as review) {
        <p>Zakończony {{ review.finishedAt }}</p>
        <ul>
          @for (finding of review.findings; track $index) {
            <li><strong>{{ finding.kind }}</strong>: {{ finding.text }} {{ finding.pages.join(', ') }}</li>
          } @empty { <li>Bez ustaleń.</li> }
        </ul>
      } @else {
        <p>Jeszcze nie było przeglądu.</p>
      }
    }
  `,
})
export class HealthView implements OnInit {
  private readonly api = inject(ApiService);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly health = signal<Health | null>(null);
  readonly message = signal<string | null>(null);

  ngOnInit(): void {
    this.api.get<Health>(`/knowledge-bases/${this.kbId()}/health`).subscribe((health) => this.health.set(health));
  }

  runReview(): void {
    this.api.post(`/knowledge-bases/${this.kbId()}/lint-runs`, {}, true).subscribe({
      next: () => {
        this.message.set('Przegląd dodany do kolejki.');
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) =>
        this.message.set((response.error as ApiError | null)?.detail ?? 'Nie udało się uruchomić przeglądu.'),
    });
  }
}
