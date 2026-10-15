import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ApiError, Health } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { apiErrorMessage, findingKindLabel } from '../../shared/labels';

@Component({
  selector: 'app-health',
  standalone: true,
  imports: [MatButtonModule, DateTimePipe],
  template: `
    @if (health(); as h) {
      <p>
        Index: ≈ {{ h.indexTokens }} / {{ h.indexCeilingTokens }} tokens
        @if (h.prefilterDue) {
          <strong> — prefilter needed</strong>
        }
      </p>
      <button mat-flat-button type="button" (click)="runReview()">Run a full review</button>
      @if (message(); as text) {
        <p role="status">{{ text }}</p>
      }
      <h2>Broken links</h2>
      <ul>
        @for (link of h.danglingLinks; track $index) {
          <li>{{ link.sourcePath }} → {{ link.targetPath }}</li>
        } @empty { <li>None.</li> }
      </ul>
      <h2>Links to the wrong directory</h2>
      <ul>
        @for (link of h.wrongDirectoryLinks; track $index) {
          <li>{{ link.sourcePath }} → {{ link.targetPath }} (did you mean {{ link.livePath }}?)</li>
        } @empty { <li>None.</li> }
      </ul>
      <h2>Concepts with no incoming links</h2>
      <ul>
        @for (path of h.orphanConcepts; track path) { <li>{{ path }}</li> } @empty { <li>None.</li> }
      </ul>
      <h2>Duplicate titles</h2>
      <ul>
        @for (dup of h.duplicateConceptTitles; track dup.title) {
          <li>{{ dup.title }}: {{ dup.paths.join(', ') }}</li>
        } @empty { <li>None.</li> }
      </ul>
      <h2>Dangling supersession notes</h2>
      <ul>
        @for (row of h.danglingSupersessions; track row.supersessionId) {
          <li>{{ row.supersededPath }}#{{ row.sectionAnchor }} ← {{ row.supersedingPath ?? '(deleted page)' }}</li>
        } @empty { <li>None.</li> }
      </ul>
      <h2>Latest full review</h2>
      @if (h.latestReview; as review) {
        <p>Finished {{ review.finishedAt | dateTime }}</p>
        <ul>
          @for (finding of review.findings; track $index) {
            <li><strong>{{ kindLabel(finding.kind) }}</strong>: {{ finding.text }} {{ finding.pages.join(', ') }}</li>
          } @empty { <li>No findings.</li> }
        </ul>
      } @else {
        <p>No review yet.</p>
      }
    }
  `,
})
export class HealthView implements OnInit {
  private readonly wiki = inject(WikiApi);
  private readonly runs = inject(RunApi);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly health = signal<Health | null>(null);
  readonly message = signal<string | null>(null);
  protected readonly kindLabel = findingKindLabel;

  ngOnInit(): void {
    this.wiki.health(this.kbId()).subscribe((health) => this.health.set(health));
  }

  runReview(): void {
    this.runs.startLint(this.kbId()).subscribe({
      next: () => {
        this.message.set('The review is queued.');
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) =>
        this.message.set(apiErrorMessage(response.error as ApiError | null, 'The review could not be started.')),
    });
  }
}
