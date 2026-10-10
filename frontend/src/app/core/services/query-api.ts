import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Answer, QuerySession, RunAccepted } from '../models/api';
import { ApiService } from './api.service';

/** Owns the endpoint paths of Query sessions: questions and wiki edits. */
@Injectable({ providedIn: 'root' })
export class QueryApi {
  private readonly api = inject(ApiService);

  open(kbId: string): Observable<QuerySession> {
    return this.api.post(`/knowledge-bases/${kbId}/query-sessions`);
  }

  ask(kbId: string, sessionId: string, question: string): Observable<Answer> {
    return this.api.post(`/knowledge-bases/${kbId}/query-sessions/${sessionId}/messages`, { question });
  }

  edit(kbId: string, sessionId: string, instruction: string, quotedAnswer?: string): Observable<RunAccepted> {
    return this.api.post(`/knowledge-bases/${kbId}/query-sessions/${sessionId}/edits`, { instruction, quotedAnswer });
  }
}
