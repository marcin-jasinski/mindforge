import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { ApiError, Health } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { Icon } from '../../shared/icon';
import { apiErrorMessage, findingKindLabel } from '../../shared/labels';

@Component({
  selector: 'app-health',
  standalone: true,
  imports: [RouterLink, MatButtonModule, DateTimePipe, Icon],
  template: `
    @if (health(); as h) {
      <div class="summary">
        <section class="card stack index">
          <span class="eyebrow">Index size</span>
          <p><strong class="big">{{ h.indexTokens }}</strong> <span class="muted">/ {{ h.indexCeilingTokens }} tokens</span></p>
          <div class="progress-track" role="progressbar" [attr.aria-valuenow]="indexPercent()" aria-valuemin="0" aria-valuemax="100"
            aria-label="Index size"><span [style.width.%]="indexPercent()"></span></div>
          @if (h.prefilterDue) {
            <span class="chip warn">Prefilter needed</span>
          }
        </section>
        <div class="checks">
        @for (check of checks(); track check.label) {
          <section class="card check" [class.bad]="check.count">
            <span class="icon-tile" [class.success]="!check.count" [class.warn]="check.count">
              <app-icon [name]="check.count ? 'warning' : 'check'" />
            </span>
            <div>
              <strong class="big">{{ check.count }}</strong>
              <p class="muted small">{{ check.label }}</p>
            </div>
          </section>
        }
        </div>
      </div>

      @if (!issueCount()) {
        <p class="alert success"><app-icon name="check" /> All structural checks pass. The wiki is consistent.</p>
      }

      <div class="stack-lg">
        @if (h.danglingLinks.length) {
          <section class="card" aria-labelledby="broken-heading">
            <div class="section-head"><app-icon name="linkOff" /><h2 id="broken-heading">Broken links</h2>
              <span class="muted small">A page links to a page that does not exist.</span></div>
            <ul class="list">
              @for (link of h.danglingLinks; track $index) {
                <li><a [routerLink]="pageLink(link.sourcePath)" class="mono">{{ link.sourcePath }}</a> → <span class="mono error">{{ link.targetPath }}</span></li>
              }
            </ul>
          </section>
        }
        @if (h.wrongDirectoryLinks.length) {
          <section class="card" aria-labelledby="wrong-heading">
            <div class="section-head"><h2 id="wrong-heading">Links to the wrong directory</h2></div>
            <ul class="list">
              @for (link of h.wrongDirectoryLinks; track $index) {
                <li><a [routerLink]="pageLink(link.sourcePath)" class="mono">{{ link.sourcePath }}</a> → <span class="mono">{{ link.targetPath }}</span>
                  <span class="muted"> — did you mean </span><a [routerLink]="pageLink(link.livePath)" class="mono">{{ link.livePath }}</a>?</li>
              }
            </ul>
          </section>
        }
        @if (h.orphanConcepts.length) {
          <section class="card" aria-labelledby="orphan-heading">
            <div class="section-head"><h2 id="orphan-heading">Concepts with no incoming links</h2></div>
            <ul class="list">
              @for (path of h.orphanConcepts; track path) { <li><a [routerLink]="pageLink(path)" class="mono">{{ path }}</a></li> }
            </ul>
          </section>
        }
        @if (h.duplicateConceptTitles.length) {
          <section class="card" aria-labelledby="dup-heading">
            <div class="section-head"><h2 id="dup-heading">Duplicate titles</h2></div>
            <ul class="list">
              @for (dup of h.duplicateConceptTitles; track dup.title) {
                <li><strong>{{ dup.title }}</strong>:
                  @for (path of dup.paths; track path) { <a [routerLink]="pageLink(path)" class="mono">{{ path }}</a>&nbsp; }</li>
              }
            </ul>
          </section>
        }
        @if (h.danglingSupersessions.length) {
          <section class="card" aria-labelledby="sup-heading">
            <div class="section-head"><h2 id="sup-heading">Dangling supersession notes</h2></div>
            <ul class="list">
              @for (row of h.danglingSupersessions; track row.supersessionId) {
                <li class="mono">{{ row.supersededPath }}#{{ row.sectionAnchor }} ← {{ row.supersedingPath ?? '(deleted page)' }}</li>
              }
            </ul>
          </section>
        }

        <section class="card stack" aria-labelledby="review-heading">
          <div class="section-head">
            <span class="icon-tile"><app-icon name="sparkle" /></span>
            <div>
              <h2 id="review-heading">Full review</h2>
              <p class="muted small">
                The model reads the whole wiki for contradictions, missing pages and open questions — ideas for what to study next.
              </p>
            </div>
            <span class="spacer"></span>
            <button mat-flat-button type="button" [disabled]="reviewing()" (click)="runReview()">
              <app-icon name="sparkle" /> {{ reviewing() ? 'Reviewing…' : 'Run a full review' }}
            </button>
          </div>
          @if (message(); as text) {
            <p class="alert info" role="status">{{ text }}</p>
          }
          @if (h.latestReview; as review) {
            <p class="muted small">Last review finished {{ review.finishedAt | dateTime }}</p>
            <ul class="list">
              @for (finding of review.findings; track $index) {
                <li class="finding">
                  <span class="chip accent">{{ kindLabel(finding.kind) }}</span>
                  <span>{{ finding.text }}</span>
                  @for (path of finding.pages; track path) { <a [routerLink]="pageLink(path)" class="mono small">{{ path }}</a> }
                  <a mat-button [routerLink]="['..', 'chat']" [queryParams]="{ q: finding.text }"><app-icon name="chat" /> Discuss in chat</a>
                </li>
              } @empty { <li class="muted">No findings.</li> }
            </ul>
          } @else {
            <p class="muted">No review yet.</p>
          }
        </section>
      </div>
    } @else {
      <div class="summary" aria-busy="true">
        @for (i of [1, 2, 3, 4]; track i) { <div class="skeleton" style="height: 6.5rem"></div> }
      </div>
    }
  `,
  styles: `
    .summary { display: grid; gap: 1rem; margin-bottom: 1.5rem; }
    .checks { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(min(100%, 11rem), 1fr)); }
    .index { gap: 0.6rem; }
    .big { font-size: 1.6rem; font-weight: 700; letter-spacing: -0.02em; }
    .check { display: flex; gap: 0.9rem; align-items: center; }
    .check.bad { border-color: rgb(251 191 36 / 0.3); }
    .alert { margin-bottom: 1.5rem; }
    .finding { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: baseline; }
  `,
})
export class HealthView {
  private readonly wiki = inject(WikiApi);
  private readonly runs = inject(RunApi);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly health = signal<Health | null>(null);
  readonly message = signal<string | null>(null);
  protected readonly kindLabel = findingKindLabel;

  readonly indexPercent = computed(() => {
    const h = this.health();
    return h ? Math.min(100, Math.round((h.indexTokens / h.indexCeilingTokens) * 100)) : 0;
  });
  readonly checks = computed(() => {
    const h = this.health();
    return h ? [
      { label: 'Broken links', count: h.danglingLinks.length },
      { label: 'Wrong-directory links', count: h.wrongDirectoryLinks.length },
      { label: 'Orphan concepts', count: h.orphanConcepts.length },
      { label: 'Duplicate titles', count: h.duplicateConceptTitles.length },
      { label: 'Dangling supersessions', count: h.danglingSupersessions.length },
    ] : [];
  });
  readonly issueCount = computed(() => this.checks().reduce((sum, check) => sum + check.count, 0));
  readonly reviewing = computed(() => this.progress.runs()
    .some((run) => run.kind === 'LINT' && ['QUEUED', 'RUNNING', 'WRITTEN'].includes(run.status)));

  constructor() {
    // a run that ends can change every check and brings a review's findings; the first pass loads the page
    effect(() => {
      this.progress.runs();
      this.wiki.health(this.kbId()).subscribe((health) => this.health.set(health));
    });
  }

  pageLink(path: string): string[] {
    return ['..', 'pages', ...path.split('/')];
  }

  runReview(): void {
    this.runs.startLint(this.kbId()).subscribe({
      next: () => {
        this.message.set('The review is queued. Its findings appear here when it ends.');
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) =>
        this.message.set(apiErrorMessage(response.error as ApiError | null, 'The review could not be started.')),
    });
  }
}
