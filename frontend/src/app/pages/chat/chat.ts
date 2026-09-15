import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { filter, interval, switchMap, take } from 'rxjs';

import { Answer, ApiError, QuerySession, RunAccepted, RunReport } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';
import { ProgressService } from '../../core/services/progress.service';
import { renderMarkdown } from '../../shared/markdown';
import { PageLinksDirective } from '../../shared/page-links.directive';

interface Message {
  role: 'user' | 'assistant' | 'edit';
  text: string;
  html?: string;
  cited?: string[];
  run?: RunReport;
}

const EDIT_NAMED_NO_PAGE = 'edit named no page';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, PageLinksDirective],
  templateUrl: './chat.html',
  styles: `.message { padding: 0.5rem 1rem; border-radius: 0.5rem; background: var(--mat-sys-surface-container); }
    .message.user { background: var(--mat-sys-primary-container); }`,
})
export class Chat implements OnInit {
  private readonly api = inject(ApiService);
  private readonly progress = inject(ProgressService);

  readonly kbId = input.required<string>();
  readonly messages = signal<Message[]>([]);
  readonly busy = signal(false);
  private readonly sessionId = signal('');
  readonly draft = signal('');

  ngOnInit(): void {
    this.api.post<QuerySession>(`/knowledge-bases/${this.kbId()}/query-sessions`).subscribe((session) => {
      this.sessionId.set(session.interactionId);
    });
  }

  ask(): void {
    const question = this.draft().trim();
    this.push({ role: 'user', text: question });
    this.draft.set('');
    this.busy.set(true);
    this.api.post<Answer>(this.chatPath('/messages'), { question }).subscribe({
      next: (answer) =>
        this.push({ role: 'assistant', text: answer.answer, html: renderMarkdown(answer.answer, this.kbId()), cited: answer.citedPaths }),
      complete: () => this.busy.set(false),
      error: () => this.busy.set(false),
    });
  }

  edit(quotedAnswer?: string): void {
    const instruction = quotedAnswer ? 'Zapisz tę odpowiedź w wiki.' : this.draft().trim();
    this.push({ role: 'user', text: instruction });
    this.draft.set('');
    this.api.post<RunAccepted>(this.chatPath('/edits'), { instruction, quotedAnswer }).subscribe((accepted) =>
      this.follow(accepted.runId),
    );
  }

  revert(run: RunReport): void {
    this.api.post(`/knowledge-bases/${this.kbId()}/runs/${run.runId}/revert`, {}, true).subscribe({
      next: () => {
        this.push({ role: 'edit', text: 'Cofnięto zmianę.' });
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) =>
        this.push({ role: 'edit', text: (response.error as ApiError | null)?.code === 'KNOWLEDGE_BASE_BUSY'
          ? 'Baza wiedzy jest zajęta — spróbuj cofnąć za chwilę.'
          : 'Tej zmiany nie można już cofnąć.' }),
    });
  }

  citedLink(path: string): string[] {
    return ['..', 'pages', ...path.split('/')];
  }

  /** Waits for the edit's run to end, then shows its report inline. */
  private follow(runId: string): void {
    interval(1000)
      .pipe(
        switchMap(() => this.api.get<RunReport>(`/knowledge-bases/${this.kbId()}/runs/${runId}`)),
        filter((run) => run.status === 'COMPLETED' || run.status === 'FAILED'),
        take(1),
      )
      .subscribe((run) => {
        this.progress.reload(this.kbId());
        const text = run.failureReason === EDIT_NAMED_NO_PAGE
          ? 'Nie wiem, którą stronę zmienić — nazwij stronę albo przeformułuj.'
          : run.status === 'COMPLETED' ? 'Zmieniono wiki.' : `Nie udało się: ${run.failureReason}`;
        this.push({ role: 'edit', text, run });
      });
  }

  private push(message: Message): void {
    this.messages.update((messages) => [...messages, message]);
  }

  private chatPath(suffix: string): string {
    return `/knowledge-bases/${this.kbId()}/query-sessions/${this.sessionId()}${suffix}`;
  }
}
