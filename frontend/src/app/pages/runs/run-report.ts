import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { ApiError, RunReport } from '../../core/models/api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { DiffView } from '../../shared/diff-view';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { Icon } from '../../shared/icon';
import { apiErrorMessage, failureLine, failureReasonLabel, findingKindLabel, runKindLabel, runStatusLabel } from '../../shared/labels';
import { pageRoute } from '../../shared/diff';

@Component({
  selector: 'app-run-report',
  standalone: true,
  imports: [RouterLink, MatButtonModule, DiffView, DateTimePipe, Icon],
  templateUrl: './run-report.html',
  styles: `
    :host { display: flex; flex-direction: column; gap: 1.5rem; }
    .crumb { display: inline-flex; align-items: center; gap: 0.35rem; color: var(--mf-muted); font-size: 0.85rem; }
    .head { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; justify-content: space-between; }
    .stats { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); }
    .stats .card { display: flex; flex-direction: column; gap: 0.2rem; padding: 1rem 1.25rem; }
    .stats strong { font-size: 1.6rem; letter-spacing: -0.02em; }
    .change { padding: 0; overflow: hidden; }
    .change summary { display: flex; align-items: center; gap: 0.6rem; padding: 0.9rem 1.1rem; cursor: pointer; list-style: none; }
    .change summary::-webkit-details-marker { display: none; }
    .change[open] .caret { transform: rotate(90deg); }
    .caret { transition: transform 150ms; color: var(--mf-muted); }
    .change app-diff-view { display: block; padding: 0 1.1rem 1.1rem; }
    .grow { flex: 1; }
  `,
})
export class RunReportView {
  private readonly runs = inject(RunApi);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly runId = input.required<string>();
  readonly report = signal<RunReport | null>(null);
  readonly message = signal<string | null>(null);
  readonly done = signal<string | null>(null);
  protected readonly kindLabel = runKindLabel;
  protected readonly statusLabel = runStatusLabel;
  protected readonly reasonLabel = failureReasonLabel;
  protected readonly failureLine = failureLine;
  protected readonly findingLabel = findingKindLabel;

  constructor() {
    effect(() => this.load(this.runId()));
  }

  revert(): void {
    // ponytail: native confirm; a MatDialog when the confirmation needs to show what the revert will undo
    if (confirm('Revert this run? The changes it made to the wiki will be undone.')) {
      this.act(this.runs.revert(this.kbId(), this.runId()), 'Reverted. The pages are back to their previous version.');
    }
  }

  removeSupersession(supersessionId: string): void {
    this.act(this.runs.removeSupersession(this.kbId(), supersessionId));
  }

  pageLink(path: string): string[] {
    return ['../..', ...pageRoute(path)];
  }

  private act(request: import('rxjs').Observable<unknown>, success: string | null = null): void {
    this.message.set(null);
    this.done.set(null);
    request.subscribe({
      next: () => {
        this.done.set(success);
        this.load(this.runId());
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | null;
        this.message.set(apiErrorMessage(error, 'The action failed.'));
      },
    });
  }

  private load(runId: string): void {
    this.runs.report(this.kbId(), runId).subscribe((report) => this.report.set(report));
  }
}
