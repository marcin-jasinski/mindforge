import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { filter, interval, switchMap, take } from 'rxjs';

import { ApiError, RunReport } from '../../core/models/api';
import { QueryApi } from '../../core/services/query-api';
import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { WikiApi } from '../../core/services/wiki-api';
import { renderMarkdown } from '../../shared/markdown';
import { PageLinksDirective } from '../../shared/page-links.directive';
import { apiErrorMessage, failureLine, failureReasonLabel } from '../../shared/labels';

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
  private readonly query = inject(QueryApi);
  private readonly runs = inject(RunApi);
  private readonly progress = inject(ProgressService);
  private readonly wiki = inject(WikiApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly kbId = input.required<string>();
  readonly messages = signal<Message[]>([]);
  readonly busy = signal(false);
  private readonly sessionId = signal('');
  readonly draft = signal('');
  private readonly titles = signal<Record<string, string>>({});
  protected readonly failureLine = failureLine;

  ngOnInit(): void {
    this.query.open(this.kbId()).subscribe((session) => {
      this.sessionId.set(session.interactionId);
    });
    // a citation is a page path; the reader should see the page's title
    this.wiki.index(this.kbId()).subscribe((index) =>
      this.titles.set(Object.fromEntries(index.pages.map((page) => [page.path, page.title]))),
    );
  }

  ask(): void {
    const question = this.draft().trim();
    this.push({ role: 'user', text: question });
    this.draft.set('');
    this.busy.set(true);
    this.query.ask(this.kbId(), this.sessionId(), question).subscribe({
      next: (answer) =>
        this.push({ role: 'assistant', text: answer.answer, html: renderMarkdown(answer.answer, this.kbId()), cited: answer.citedPaths }),
      complete: () => this.busy.set(false),
      error: () => {
        // the question stays in the box so it can be sent again
        this.draft.set(question);
        this.busy.set(false);
      },
    });
  }

  edit(quotedAnswer?: string): void {
    const instruction = quotedAnswer ? 'Save this answer to the wiki.' : this.draft().trim();
    this.push({ role: 'user', text: instruction });
    const typed = this.draft();
    this.draft.set('');
    this.busy.set(true);
    this.query.edit(this.kbId(), this.sessionId(), instruction, quotedAnswer).subscribe({
      next: (accepted) => this.follow(accepted.runId),
      error: () => {
        this.draft.set(typed);
        this.busy.set(false);
      },
    });
  }

  revert(run: RunReport): void {
    // ponytail: native confirm, as on the run report; a MatDialog when it needs to show what will be undone
    if (!confirm('Revert this change? The pages go back to their previous version.')) {
      return;
    }
    this.runs.revert(this.kbId(), run.runId).subscribe({
      next: () => {
        this.push({ role: 'edit', text: 'The change was reverted.' });
        this.progress.reload(this.kbId());
      },
      error: (response: HttpErrorResponse) =>
        this.push({ role: 'edit', text: apiErrorMessage(response.error as ApiError | null, 'This change can no longer be reverted.') }),
    });
  }

  citedTitle(path: string): string {
    return this.titles()[path] ?? path;
  }

  citedLink(path: string): string[] {
    return ['..', 'pages', ...path.split('/')];
  }

  /** Waits for the edit's run to end, then shows its report inline. */
  private follow(runId: string): void {
    interval(1000)
      .pipe(
        switchMap(() => this.runs.report(this.kbId(), runId)),
        filter((run) => run.status === 'COMPLETED' || run.status === 'FAILED'),
        take(1),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((run) => {
        this.progress.reload(this.kbId());
        this.busy.set(false);
        const text = run.failureReason === EDIT_NAMED_NO_PAGE
          ? 'I do not know which page to change — name the page or rephrase.'
          : run.status === 'COMPLETED' ? 'The wiki was changed.' : `Failed: ${failureReasonLabel(run.failureReason ?? '')}`;
        this.push({ role: 'edit', text, run });
      });
  }

  private push(message: Message): void {
    this.messages.update((messages) => [...messages, message]);
  }
}
