import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RunReport, RunSummary } from '../models/api';
import { ApiService } from './api.service';

/** Owns the endpoint paths of ingest, lint and edit runs. */
@Injectable({ providedIn: 'root' })
export class RunApi {
  private readonly api = inject(ApiService);

  /** The SSE stream URL: an EventSource cannot go through HttpClient. */
  progressUrl(kbId: string): string {
    return this.api.url(`/knowledge-bases/${kbId}/progress`);
  }

  runs(kbId: string): Observable<RunSummary[]> {
    return this.api.get(`/knowledge-bases/${kbId}/runs`);
  }

  report(kbId: string, runId: string): Observable<RunReport> {
    return this.api.get(`/knowledge-bases/${kbId}/runs/${runId}`);
  }

  revert(kbId: string, runId: string): Observable<unknown> {
    return this.api.post(`/knowledge-bases/${kbId}/runs/${runId}/revert`, {}, true);
  }

  removeSupersession(kbId: string, supersessionId: string): Observable<unknown> {
    return this.api.delete(`/knowledge-bases/${kbId}/supersessions/${supersessionId}`, true);
  }

  retryDocument(kbId: string, documentId: string): Observable<unknown> {
    return this.api.post(`/knowledge-bases/${kbId}/documents/${documentId}/runs`);
  }

  startLint(kbId: string): Observable<unknown> {
    return this.api.post(`/knowledge-bases/${kbId}/lint-runs`, {}, true);
  }
}
