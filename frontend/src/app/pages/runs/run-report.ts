import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { ApiError, RunReport } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';
import { ProgressService } from '../../core/services/progress.service';
import { DiffView } from '../../shared/diff-view';
import { pageRoute } from '../../shared/diff';

@Component({
  selector: 'app-run-report',
  standalone: true,
  imports: [RouterLink, MatButtonModule, DiffView],
  templateUrl: './run-report.html',
})
export class RunReportView {
  private readonly api = inject(ApiService);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly runId = input.required<string>();
  readonly report = signal<RunReport | null>(null);
  readonly message = signal<string | null>(null);

  constructor() {
    effect(() => this.load(this.runId()));
  }

  revert(): void {
    this.act(this.api.post(`/knowledge-bases/${this.kbId()}/runs/${this.runId()}/revert`, {}, true));
  }

  removeSupersession(supersessionId: string): void {
    this.act(this.api.delete(`/knowledge-bases/${this.kbId()}/supersessions/${supersessionId}`, true));
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
        this.message.set(error?.code === 'KNOWLEDGE_BASE_BUSY'
          ? 'Baza wiedzy jest zajęta innym przebiegiem — spróbuj za chwilę.'
          : (error?.detail ?? 'Nie udało się.'));
      },
    });
  }

  private load(runId: string): void {
    this.api.get<RunReport>(`/knowledge-bases/${this.kbId()}/runs/${runId}`).subscribe((report) => this.report.set(report));
  }
}
