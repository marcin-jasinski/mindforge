import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { ApiError, RunReport } from '../../core/models/api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { DiffView } from '../../shared/diff-view';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { apiErrorMessage, failureLine, failureReasonLabel, findingKindLabel, runKindLabel, runStatusLabel } from '../../shared/labels';
import { pageRoute } from '../../shared/diff';

@Component({
  selector: 'app-run-report',
  standalone: true,
  imports: [RouterLink, MatButtonModule, DiffView, DateTimePipe],
  templateUrl: './run-report.html',
})
export class RunReportView {
  private readonly runs = inject(RunApi);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly runId = input.required<string>();
  readonly report = signal<RunReport | null>(null);
  readonly message = signal<string | null>(null);
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
      this.act(this.runs.revert(this.kbId(), this.runId()));
    }
  }

  removeSupersession(supersessionId: string): void {
    this.act(this.runs.removeSupersession(this.kbId(), supersessionId));
  }

  pageLink(path: string): string[] {
    return ['../..', ...pageRoute(path)];
  }

  private act(request: import('rxjs').Observable<unknown>): void {
    this.message.set(null);
    request.subscribe({
      next: () => {
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
