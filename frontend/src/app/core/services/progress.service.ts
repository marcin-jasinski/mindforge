import { Injectable, NgZone, OnDestroy, inject, signal } from '@angular/core';

import { RunProgress, RunSummary } from '../models/api';
import { ApiService } from './api.service';

/**
 * Follows every run of one knowledge base: on (re)connect it reads the run list, then applies progress messages on
 * top. A missed message is repaired by the next read, never redelivered.
 */
@Injectable()
export class ProgressService implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly zone = inject(NgZone);
  private source: EventSource | null = null;
  private readonly _runs = signal<RunSummary[]>([]);
  private readonly _steps = signal<Record<string, RunProgress>>({});

  readonly runs = this._runs.asReadonly();
  readonly steps = this._steps.asReadonly();

  watch(kbId: string): void {
    this.close();
    this.source = new EventSource(this.api.url(`/knowledge-bases/${kbId}/progress`), { withCredentials: true });
    this.source.onopen = () => this.reload(kbId);
    this.source.addEventListener('progress', (event) =>
      this.zone.run(() => this.apply(kbId, JSON.parse((event as MessageEvent).data) as RunProgress)),
    );
    this.reload(kbId);
  }

  reload(kbId: string): void {
    this.api.get<RunSummary[]>(`/knowledge-bases/${kbId}/runs`).subscribe((runs) => this._runs.set(runs));
  }

  ngOnDestroy(): void {
    this.close();
  }

  private apply(kbId: string, progress: RunProgress): void {
    this._steps.update((steps) => ({ ...steps, [progress.runId]: progress }));
    const known = this._runs().some((run) => run.runId === progress.runId);
    if (!known || progress.step === null) {
      // a new run or a status change: counts and reasons come from the list, not the message
      this.reload(kbId);
    }
  }

  private close(): void {
    this.source?.close();
    this.source = null;
  }
}
